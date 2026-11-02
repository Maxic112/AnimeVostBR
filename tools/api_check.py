"""Health check of every source AnimeVostBR depends on (the API is private and may change).

Usage:  python tools/api_check.py            full report
        python tools/api_check.py --quiet    summary only (for schedulers)
Logs:   logs/api_health.jsonl (one JSON line per run), logs/api_health.log (text).
Exit code: 0 — all good, 1 — warnings, 2 — something is broken.

Response FORMAT is checked too, not just availability: if the API starts returning different
fields, the app silently shows empty lists — this script catches that.
Standard library only, Python 3.8+.
"""
import concurrent.futures
import datetime
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "AnimeVostBR/1.0 (Android; client)"
API = "https://api.animevost.org/v1"
MAIN = "animevost.org"
DEFAULT_MIRROR = "v14.vost.pw"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG_DIR = os.path.join(ROOT, "logs")
STATE = os.path.join(LOG_DIR, "mirror.txt")  # last mirror found
MIRROR_RE = re.compile(r"\b(v(\d{1,3})\.vost\.pw)\b", re.I)

OK, WARN, FAIL = "OK", "WARN", "FAIL"


def http(url, data=None, headers=None, timeout=20):
    """Returns (status, body, final URL); HTTP errors are returned as a status, not raised."""
    h = {"User-Agent": UA}
    h.update(headers or {})
    body = urllib.parse.urlencode(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace"), r.geturl()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), url


class Report:
    def __init__(self):
        self.checks = []

    def run(self, name, fn):
        t = time.time()
        try:
            status, detail = fn()
        except Exception as e:  # network, parsing, unexpected format
            status, detail = FAIL, f"{type(e).__name__}: {e}"
        ms = int((time.time() - t) * 1000)
        self.checks.append({"name": name, "status": status, "detail": detail, "ms": ms})
        return status

    @property
    def worst(self):
        s = {c["status"] for c in self.checks}
        return FAIL if FAIL in s else WARN if WARN in s else OK


ctx = {}  # shared between steps: sample title, episode, mirror


def need_fields(obj, fields, where):
    missing = [f for f in fields if f not in obj]
    if missing:
        raise ValueError(f"{where}: нет полей {missing} — сменился формат API")


def check_last():
    code, body, _ = http(f"{API}/last?page=1&quantity=5")
    if code != 200:
        return FAIL, f"HTTP {code}"
    j = json.loads(body)
    data = j.get("data") or []
    if not data:
        return FAIL, "пустой data"
    t = data[0]
    need_fields(t, ["id", "title", "series", "urlImagePreview", "rating", "votes"], "/last")
    ctx["title"] = t
    total = (j.get("state") or {}).get("count")
    if "серия" not in t["series"] and t["series"] not in ("", "{}"):
        return WARN, f"неожиданный формат series: {t['series'][:60]}"
    # known quirk: without page+quantity the API returns a single title
    code2, body2, _ = http(f"{API}/last")
    one = len(json.loads(body2).get("data") or []) if code2 == 200 else -1
    note = "" if one == 1 else f"; без параметров теперь {one} тайтл(ов) — поведение изменилось"
    return (OK if not note else WARN), f"тайтлов: {len(data)}, в каталоге: {total}{note}"


def check_playlist():
    tid = ctx.get("title", {}).get("id", 4032)
    code, body, _ = http(f"{API}/playlist", data={"id": tid})
    if code != 200:
        return FAIL, f"HTTP {code}"
    items = json.loads(body)
    if not isinstance(items, list) or not items:
        return FAIL, "не массив или пусто — сменился формат"
    need_fields(items[0], ["name", "hd", "std"], "/playlist")
    ctx["episode"] = items[0]
    return OK, f"id {tid}: серий {len(items)}"


def check_info():
    tid = ctx.get("title", {}).get("id", 4032)
    code, body, _ = http(f"{API}/info", data={"id": tid})
    if code != 200:
        return FAIL, f"HTTP {code}"
    data = json.loads(body).get("data") or []
    if not data:
        return FAIL, "пустой data"
    need_fields(data[0], ["id", "title", "description"], "/info")
    return OK, data[0]["title"].split(" / ")[0][:50]


def check_search():
    code, body, _ = http(f"{API}/search", data={"name": "naruto"})
    if code != 200:
        return FAIL, f"HTTP {code}"
    n = len(json.loads(body).get("data") or [])
    if n == 0:
        return FAIL, "по «naruto» пусто"
    code404, _, _ = http(f"{API}/search", data={"name": "zzzqqqxx"})
    note = "" if code404 == 404 else f"; «ничего не найдено» теперь HTTP {code404}"
    return (OK if not note else WARN), f"найдено: {n}{note}"


def probe(url):
    code, _, final = http(url, headers={"Range": "bytes=0-1023"})
    host = urllib.parse.urlparse(final).hostname
    return (OK if code in (200, 206) else FAIL), f"HTTP {code} · {host}"


def check_video(kind):
    ep = ctx.get("episode")
    if not ep:
        return WARN, "нет серии для проверки (playlist не прошёл)"
    return probe(ep[kind])


def check_poster():
    url = ctx.get("title", {}).get("urlImagePreview", "")
    if not url:
        return WARN, "нет ссылки"
    # the app uses http: static.openni.ru has an expired HTTPS certificate
    return probe(url.replace("https://static.openni.ru/", "http://static.openni.ru/"))


def is_site(html):
    return "animevost" in html.lower() and "/tip/" in html


def check_site(host):
    code, body, final = http(f"https://{host}/")
    if code != 200:
        return FAIL, f"HTTP {code}"
    if not is_site(body):
        return WARN, "отвечает, но это не страница AnimeVost"
    moved = urllib.parse.urlparse(final).hostname
    return OK, "открывается" + (f" (перенаправил на {moved})" if moved != host else "")


def find_mirror():
    """Same logic as the app (Sites.refreshMirror)."""
    current = DEFAULT_MIRROR
    if os.path.exists(STATE):
        current = open(STATE, encoding="utf-8").read().strip() or DEFAULT_MIRROR
    found = {}

    def add(text, src):
        for m in MIRROR_RE.finditer(text or ""):
            found.setdefault(m.group(1).lower(), src)

    for url, src in [(f"https://{current}/", "редирект"), (f"https://{MAIN}/", "главная"),
                     (f"https://{MAIN}/zerkalo.html", "страница «Зеркало»"), (f"https://{current}/rss.xml", "RSS")]:
        try:
            code, body, final = http(url, timeout=12)
            add(urllib.parse.urlparse(final).hostname if src == "редирект" else body, src)
        except Exception:
            pass
    n = int(MIRROR_RE.search(current).group(2))
    for k in range(n + 1, n + 4):
        found.setdefault(f"v{k}.vost.pw", "перебор")
    found.setdefault(current, "сохранённый")
    def alive(host):  # nonexistent domains wait for a timeout, so check in parallel
        try:
            code, body, _ = http(f"https://{host}/", timeout=12)
            return code == 200 and is_site(body)
        except Exception:
            return False

    with concurrent.futures.ThreadPoolExecutor(len(found)) as pool:
        ok = dict(zip(found, pool.map(alive, list(found))))
    for host in sorted(found, key=lambda h: -int(MIRROR_RE.search(h).group(2))):
        if ok[host]:
            ctx["mirror"] = host
            os.makedirs(LOG_DIR, exist_ok=True)
            open(STATE, "w", encoding="utf-8").write(host)
            if host != current:
                return WARN, f"ЗЕРКАЛО СМЕНИЛОСЬ: {current} → {host} (нашли: {found[host]})"
            return OK, f"{host} (нашли: {found[host]})"
    ctx["mirror"] = current
    return FAIL, f"ни одно зеркало не открывается (кандидаты: {', '.join(found)})"


def check_site_fallback():
    """The app's fallback source: the episode list in the title page HTML."""
    host = ctx.get("mirror", DEFAULT_MIRROR)
    tid = ctx.get("title", {}).get("id", 4032)
    code, body, _ = http(f"https://{host}/index.php?newsid={tid}")
    if code != 200:
        return FAIL, f"HTTP {code}"
    m = re.search(r"var\s+data\s*=\s*\{([^}]*)\}", body)
    if not m:
        return FAIL, "на странице нет var data — сменилась разметка, резерв не сработает"
    n = len(re.findall(r'"([^"]+)"\s*:\s*"(\d+)"', m.group(1)))
    feed = len(re.findall(r'<article class="post"', http(f"https://{host}/")[1]))
    if feed == 0:
        return WARN, f"серий на странице: {n}, но лента не разобрана (нет article.post)"
    return OK, f"{host}: серий {n}, тайтлов в ленте {feed}"


def main():
    quiet = "--quiet" in sys.argv
    r = Report()
    r.run("API: лента /v1/last", check_last)
    r.run("API: серии /v1/playlist", check_playlist)
    r.run("API: карточка /v1/info", check_info)
    r.run("API: поиск /v1/search", check_search)
    r.run("Видео HD", lambda: check_video("hd"))
    r.run("Видео SD", lambda: check_video("std"))
    r.run("Постеры", check_poster)
    r.run(f"Основной сайт {MAIN}", lambda: check_site(MAIN))
    r.run("Зеркало (поиск актуального)", find_mirror)
    r.run("Резерв: серии и лента с зеркала", check_site_fallback)

    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    icons = {OK: "OK  ", WARN: "WARN", FAIL: "FAIL"}
    lines = [f"[{now}] AnimeVostBR — проверка источников: {r.worst}"]
    lines += [f"  {icons[c['status']]} {c['name']:<36} {c['ms']:>6} мс  {c['detail']}" for c in r.checks]

    os.makedirs(LOG_DIR, exist_ok=True)
    with open(os.path.join(LOG_DIR, "api_health.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps({"time": now, "result": r.worst, "checks": r.checks}, ensure_ascii=False) + "\n")
    with open(os.path.join(LOG_DIR, "api_health.log"), "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n\n")

    out = lines[:1] if quiet and r.worst == OK else lines
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))
    sys.exit({OK: 0, WARN: 1, FAIL: 2}[r.worst])


if __name__ == "__main__":
    main()
