package com.animevostbr.core

import com.google.gson.JsonParseException
import retrofit2.HttpException
import javax.net.ssl.SSLException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
  * Connection self-test: checks every link the app depends on — API, video and image hosts,
  * site, mirror and the HTML fallback — so a breakage of the private API is easy to pinpoint.
  */
object Diagnostics {

    enum class Status { RUNNING, OK, WARN, FAIL }

    data class Check(
        val name: String,
        val status: Status = Status.RUNNING,
        val detail: String = "",
        val ms: Long = 0
    )

    /** Runs all checks; [onUpdate] receives the current list after every step. */
    suspend fun run(onUpdate: (List<Check>) -> Unit) = withContext(Dispatchers.IO) {
        val results = mutableListOf<Check>()

        suspend fun step(name: String, block: suspend () -> Pair<Status, String>) {
            results += Check(name)
            withContext(Dispatchers.Main) { onUpdate(results.toList()) }
            val start = System.currentTimeMillis()
            val (status, detail) = try {
                block()
            } catch (e: Exception) {
                Status.FAIL to humanize(e)
            }
            results[results.lastIndex] = Check(name, status, detail, System.currentTimeMillis() - start)
            withContext(Dispatchers.Main) { onUpdate(results.toList()) }
        }

        var sample: Title? = null
        var episode: PlaylistItem? = null

        step("API: лента (/v1/last)") {
            val list = TitlesParser.parseList(ApiClient.api.last(1, 5).string())
            sample = list.firstOrNull()
            when {
                list.isEmpty() -> Status.FAIL to "Пустой ответ или новый формат"
                list.first().series.isEmpty() -> Status.WARN to "Тайтлов: ${list.size}, но нет поля series"
                else -> Status.OK to "Тайтлов: ${list.size}"
            }
        }
        val id = sample?.id ?: 4032
        step("API: серии (/v1/playlist)") {
            val items = PlaylistParser.parse(ApiClient.api.playlist(id.toString()).string())
            episode = items.firstOrNull()
            when {
                items.isEmpty() -> Status.FAIL to "Серии не получены"
                items.first().sd == null -> Status.WARN to "Серий: ${items.size}, нет SD-ссылки (поле std)"
                else -> Status.OK to "Серий: ${items.size}"
            }
        }
        step("API: карточка (/v1/info)") {
            val t = TitlesParser.parseSingle(ApiClient.api.info(id.toString()).string())
            if (t == null) Status.FAIL to "Карточка не получена" else Status.OK to t.title.substringBefore(" / ").take(40)
        }
        step("API: поиск (/v1/search)") {
            val n = TitlesParser.parseList(ApiClient.api.search("naruto").string()).size
            if (n == 0) Status.FAIL to "По запросу «naruto» пусто" else Status.OK to "Найдено: $n"
        }
        // API is down: probe video and posters using links from the site fallback
        if (episode == null) episode = runCatching { SiteSource.playlist(id).firstOrNull() }.getOrNull()
        if (sample == null) sample = runCatching { SiteSource.feed(1).second.firstOrNull() }.getOrNull()
        step("Видео HD") { probe(episode?.hd) }
        step("Видео SD") { probe(episode?.sd) }
        step("Постеры") { probe(sample?.poster) }
        step("Основной сайт (${Sites.MAIN_HOST})") { site("https://${Sites.MAIN_HOST}/") }
        step("Зеркало (${Sites.mirrorHost})") { site("https://${Sites.mirrorHost}/") }
        step("Резерв: лента с сайта") {
            val (base, list) = SiteSource.feed(1)
            if (list.isEmpty()) Status.FAIL to "Не разобрана (сменилась разметка?)"
            else Status.OK to "Тайтлов: ${list.size} · ${base.substringAfter("://")}"
        }
        step("Резерв: серии с сайта") {
            val n = SiteSource.playlist(id).size
            if (n == 0) Status.FAIL to "Не найден список серий на странице" else Status.OK to "Серий: $n"
        }
        step("Поиск по сайту") {
            val n = SiteSearch.search("naruto").size
            if (n == 0) Status.WARN to "Ничего не найдено" else Status.OK to "Найдено: $n"
        }
    }

    /** Requests only the first KiB of the file. */
    private fun probe(url: String?): Pair<Status, String> {
        if (url.isNullOrBlank()) return Status.WARN to "Нет ссылки для проверки"
        val request = Request.Builder().url(url)
            .header("User-Agent", ApiClient.UA)
            .header("Range", "bytes=0-1023")
            .build()
        ApiClient.ok.newCall(request).execute().use { r ->
            val host = r.request.url.host
            return if (r.code == 200 || r.code == 206) Status.OK to "HTTP ${r.code} · $host"
            else Status.FAIL to "HTTP ${r.code} · $host"
        }
    }

    private fun site(url: String): Pair<Status, String> {
        val html = Sites.get(url)
        return if (Sites.looksLikeSite(html)) Status.OK to "Открывается"
        else Status.WARN to "Отвечает, но это не страница AnimeVost"
    }

    fun humanize(e: Throwable): String = when (e) {
        is UnknownHostException -> "Сервер не найден: нет интернета или адрес заблокирован провайдером"
        is SocketTimeoutException -> "Сервер не ответил вовремя"
        is ConnectException -> "Сервер отклонил соединение"
        is SSLException -> "Ошибка защищённого соединения (сертификат)"
        is HttpException -> "Сервер ответил ошибкой HTTP ${e.code()}"
        is JsonParseException, is IllegalStateException -> "Неожиданный формат ответа — API изменился"
        else -> e.message ?: e.javaClass.simpleName
    }

    fun summary(checks: List<Check>): String {
        fun failed(prefix: String) = checks.filter { it.name.startsWith(prefix) }.any { it.status == Status.FAIL }
        return when {
            checks.any { it.status == Status.RUNNING } -> "Проверяем…"
            checks.none { it.status == Status.FAIL } -> "Всё работает"
            failed("API") && !failed("Резерв") -> "API не отвечает — приложение работает через сайт"
            failed("API") && failed("Резерв") -> "Не работают ни API, ни сайт — проверьте интернет или VPN"
            failed("Видео") -> "Видеосервер недоступен — серии не будут воспроизводиться"
            else -> "Есть проблемы — подробности ниже"
        }
    }
}
