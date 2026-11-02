"""AnimeVostBR launcher icon: a samurai against the moon under a sakura tree, with "BR".
Drawn in 1000x1000 units with 4x supersampling.
Output: adaptive icon layers (background, foreground, monochrome), previews and the TV banner.

Usage: python tools/make_icon.py <output dir> [path to a bold font, default: Impact]"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1]
FONT = sys.argv[2] if len(sys.argv) > 2 else 'impact.ttf'  # Pillow also searches system font dirs
os.makedirs(OUT, exist_ok=True)
SS = 4
N = 1000 * SS // 4 * 4  # 4000 px canvas for a 1000-unit layout
K = N / 1000.0

random.seed(7)


def P(pts):
    return [(x * K, y * K) for x, y in pts]


def smooth(pts, steps=10, closed=True):
    """Catmull-Rom spline through the control points."""
    out = []
    n = len(pts)
    rng = range(n) if closed else range(n - 1)
    for i in rng:
        p0 = pts[(i - 1) % n] if closed or i > 0 else pts[i]
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        p3 = pts[(i + 2) % n] if closed or i + 2 < n else p2
        for k in range(steps):
            t = k / steps
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                       + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                       + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    return out


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def gradient(w, h, stops):
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        for i in range(len(stops) - 1):
            if stops[i][0] <= t <= stops[i + 1][0]:
                tt = (t - stops[i][0]) / (stops[i + 1][0] - stops[i][0])
                c = lerp(stops[i][1], stops[i + 1][1], tt)
                break
        for x in range(w):
            px[x, y] = c
    return img


def glow(size, center, radius, color, blur):
    layer = Image.new('RGBA', size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
    return layer.filter(ImageFilter.GaussianBlur(blur))


def petal(d, cx, cy, r, ang, color):
    pts = []
    for i in range(24):
        t = 2 * math.pi * i / 24
        x = math.cos(t) * r
        y = math.sin(t) * r * 0.55
        ca, sa = math.cos(ang), math.sin(ang)
        pts.append((cx + x * ca - y * sa, cy + x * sa + y * ca))
    d.polygon(pts, fill=color)


# ---------------------------------------------------------------- background
def draw_background(w=1000, h=1000, ox=0, oy=0, scale=1.0, tree=True):
    """Scene in 1000x1000 units; offset/scale are used for the banner."""
    W, H = int(w * K), int(h * K)
    img = gradient(W, H, [
        (0.0, hexc('#0E0726')),
        (0.40, hexc('#36125E')),
        (0.72, hexc('#B23E7C')),
        (1.0, hexc('#F7A1B5')),
    ])

    def T(x, y):
        return ((x * scale + ox) * K, (y * scale + oy) * K)

    # stars
    d = ImageDraw.Draw(img)
    for _ in range(int(60 * w / 1000)):
        x, y = random.uniform(0, w), random.uniform(0, h * 0.45)
        r = random.uniform(1.2, 3.2) * K
        a = random.randint(120, 230)
        d.ellipse([x * K - r, y * K - r, x * K + r, y * K + r], fill=(255, 255, 255, a))

    # moon with glow
    mx, my = T(600, 420)
    mr = 190 * scale * K
    img = Image.alpha_composite(img, glow(img.size, (mx, my), mr * 1.55, (255, 200, 220, 110), 60 * K))
    img = Image.alpha_composite(img, glow(img.size, (mx, my), mr * 1.15, (255, 230, 235, 140), 25 * K))
    moon = Image.new('RGBA', img.size, (0, 0, 0, 0))
    md = ImageDraw.Draw(moon)
    md.ellipse([mx - mr, my - mr, mx + mr, my + mr], fill=hexc('#FFF3E3'))
    # faint maria on the moon
    for (dx, dy, rr) in [(-60, -40, 38), (40, 30, 52), (-20, 80, 28), (70, -70, 22)]:
        cx, cy = mx + dx * scale * K, my + dy * scale * K
        rr *= scale * K
        md.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(252, 236, 228, 255))
    img = Image.alpha_composite(img, moon)

    # distant mountains
    mount = Image.new('RGBA', img.size, (0, 0, 0, 0))
    md = ImageDraw.Draw(mount)
    md.polygon([T(*p) for p in [(-50, 760), (120, 640), (230, 690), (380, 560), (470, 610), (520, 590),
                                 (700, 680), (860, 600), (1050, 720), (1050, 1000), (-50, 1000)]],
               fill=hexc('#5B2470', 255))
    img = Image.alpha_composite(img, mount.filter(ImageFilter.GaussianBlur(2 * K)))

    # hill
    hill = Image.new('RGBA', img.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(hill)
    cx, cy = T(520, 1080)
    rx, ry = 720 * scale * K, 380 * scale * K
    hd.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=hexc('#1A0A26'))
    img = Image.alpha_composite(img, hill)

    if tree:
        img = Image.alpha_composite(img, draw_tree(img.size, T, scale))
    return img


def draw_tree(size, T, scale):
    layer = Image.new('RGBA', size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    bark = hexc('#2B0F22')
    branches = [
        # (points, width)
        ([(40, 1010), (90, 860), (150, 720), (190, 600), (230, 500)], 46),
        ([(230, 500), (300, 400), (380, 300), (470, 220)], 26),
        ([(230, 500), (170, 380), (120, 260), (90, 150)], 22),
        ([(300, 400), (360, 420), (430, 400)], 12),
        ([(380, 300), (430, 260), (520, 250)], 12),
        ([(170, 380), (110, 380), (40, 340)], 12),
        ([(120, 260), (180, 190), (250, 150)], 10),
        ([(470, 220), (540, 170), (600, 150)], 10),
        ([(190, 600), (120, 560), (60, 560)], 14),
    ]
    tips = []
    for pts, wdt in branches:
        pp = [T(*p) for p in pts]
        width = int(wdt * scale * K)
        d.line(pp, fill=bark, width=width, joint='curve')
        for p in pp:
            r = width / 2
            d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=bark)
        tips += pts[1:]
    # blossom clouds: soft base, hundreds of petals, separate flowers
    pinks = ['#FFC1D3', '#FF9EBB', '#FF85AB', '#FFD9E4', '#F06292', '#FFB3C8']
    centers = [(470, 220), (520, 250), (600, 150), (430, 400), (380, 300), (90, 150), (250, 150),
               (40, 340), (120, 260), (300, 400), (180, 190), (60, 560), (230, 480), (350, 230), (520, 180)]
    base = Image.new('RGBA', size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(base)
    for (x, y) in centers:
        for _ in range(9):
            px, py = T(x + random.gauss(0, 40), y + random.gauss(0, 28))
            r = random.uniform(40, 70) * scale * K
            bd.ellipse([px - r, py - r * 0.8, px + r, py + r * 0.8], fill=hexc('#E86C9A', 235))
    layer = Image.alpha_composite(layer, base.filter(ImageFilter.GaussianBlur(10 * scale * K)))
    pd = ImageDraw.Draw(layer)
    for (x, y) in centers:
        for _ in range(120):
            px, py = T(x + random.gauss(0, 52), y + random.gauss(0, 36))
            petal(pd, px, py, random.uniform(9, 17) * scale * K, random.uniform(0, math.pi),
                  hexc(random.choice(pinks), random.randint(215, 255)))
    # five-petal flowers with a dark centre
    for (x, y) in centers:
        for _ in range(7):
            fx, fy = T(x + random.gauss(0, 45), y + random.gauss(0, 30))
            rr = random.uniform(9, 14) * scale * K
            rot = random.uniform(0, 2 * math.pi)
            for k in range(5):
                ang = rot + k * 2 * math.pi / 5
                petal(pd, fx + math.cos(ang) * rr, fy + math.sin(ang) * rr, rr * 1.05, ang, hexc('#FFE3EC'))
            c = rr * 0.35
            pd.ellipse([fx - c, fy - c, fx + c, fy + c], fill=hexc('#D81B60'))
    # petals in the air
    d = ImageDraw.Draw(layer)
    for _ in range(26):
        x, y = random.uniform(150, 980), random.uniform(250, 760)
        px, py = T(x, y)
        petal(d, px, py, random.uniform(8, 15) * scale * K, random.uniform(0, math.pi),
              hexc(random.choice(pinks), random.randint(190, 255)))
    return layer


# ---------------------------------------------------------------- samurai
def samurai_shapes(cx=560, base=705, s=1.0):
    """Silhouette in a kasa hat: smooth body outline, katana at the hip, scarf in the wind."""
    def S(pts):
        return [(cx + (x - 560) * s, base + (y - 705) * s) for x, y in pts]
    hat = S(smooth([(452, 368), (505, 343), (546, 314), (561, 300), (576, 314), (617, 343), (670, 368),
                    (624, 377), (561, 381), (498, 377)], 6))
    body = S(smooth([
        (542, 380), (580, 380), (584, 396),            # neck
        (622, 404), (652, 424), (660, 470), (676, 512),  # right shoulder and sleeve
        (642, 526), (630, 552),                          # belt, right
        (648, 610), (668, 668), (684, 702),              # hakama, right, slightly windblown
        (594, 704), (566, 650), (548, 704),              # gap between the legs
        (446, 702), (462, 660), (474, 604), (496, 552),  # hakama, left
        (488, 528), (450, 518), (462, 466), (476, 424),  # left sleeve
        (506, 404), (538, 396),
    ], 8))
    sword = S([(372, 606), (402, 598), (436, 588)]), S([(436, 588), (600, 546), (716, 512)])
    guard = S([(438, 588)])[0]
    scarf = S(smooth([(580, 384), (622, 372), (676, 350), (726, 330), (782, 326), (752, 346), (712, 366),
                      (664, 392), (604, 404)], 6))
    return hat, body, sword, guard, scarf


def draw_samurai(img, cx=560, base=705, s=1.0, mono=False):
    hat, body, sword, guard, scarf = samurai_shapes(cx, base, s)
    ink = (255, 255, 255, 255) if mono else hexc('#0B0512')

    def silhouette(d, color, dx=0.0):
        sh = lambda pts: [((x + dx) * K, y * K) for x, y in pts]
        d.polygon(sh(body), fill=color)
        d.polygon(sh(hat), fill=color)
        (h1, h2) = sword
        d.line(sh(h2), fill=color, width=int(12 * s * K), joint='curve')
        d.line(sh(h1), fill=color, width=int(17 * s * K), joint='curve')
        gx, gy = guard
        r = 17 * s * K
        gx = (gx + dx) * K
        d.ellipse([gx - r * 0.55, gy * K - r, gx + r * 0.55, gy * K + r], fill=color)

    if mono:
        silhouette(ImageDraw.Draw(img), ink)
        return
    d = ImageDraw.Draw(img)
    d.polygon(P(scarf), fill=hexc('#E0245E'))
    # moon rim light: a pink silhouette shifted right, the black one on top
    rim = Image.new('RGBA', img.size, (0, 0, 0, 0))
    silhouette(ImageDraw.Draw(rim), hexc('#FF9EC4', 230), dx=4.5 * s)
    img.alpha_composite(rim.filter(ImageFilter.GaussianBlur(1.5 * K)))
    silhouette(d, ink)


# ---------------------------------------------------------------- "BR" text
def draw_text(img, text, cx, cy, height, mono=False, max_w=None):
    font_path = FONT
    size = int(height * K * 1.25)
    font = ImageFont.truetype(font_path, size)
    tmp = ImageDraw.Draw(img)
    bbox = tmp.textbbox((0, 0), text, font=font, stroke_width=int(10 * K))
    while max_w and bbox[2] - bbox[0] > max_w * K:
        size = int(size * 0.95)
        font = ImageFont.truetype(font_path, size)
        bbox = tmp.textbbox((0, 0), text, font=font, stroke_width=int(10 * K))
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = cx * K - tw / 2 - bbox[0]
    y = cy * K - th / 2 - bbox[1]
    if mono:
        tmp.text((x, y), text, font=font, fill=(255, 255, 255, 255))
        return
    # pink glow
    g = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(g).text((x, y), text, font=font, fill=hexc('#FF4F8B', 230), stroke_width=int(14 * K),
                           stroke_fill=hexc('#FF4F8B', 230))
    img.alpha_composite(g.filter(ImageFilter.GaussianBlur(14 * K)))
    # outline
    ImageDraw.Draw(img).text((x, y), text, font=font, fill=hexc('#1A0A26'), stroke_width=int(9 * K),
                             stroke_fill=hexc('#1A0A26'))
    # white → pink gradient fill
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).text((x, y), text, font=font, fill=255)
    top, bot = int(cy * K - th / 2), int(cy * K + th / 2)
    grad = Image.new('RGBA', img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for yy in range(max(top, 0), min(bot + 1, img.size[1])):
        t = (yy - top) / max(1, bot - top)
        gd.line([(0, yy), (img.size[0], yy)], fill=lerp(hexc('#FFFFFF'), hexc('#FF9CC0'), t))
    img.paste(grad, (0, 0), mask)


def down(img, px):
    return img.resize((px, px), Image.LANCZOS)


# ---------------------------------------------------------------- assembly
bg = draw_background()
fg = Image.new('RGBA', bg.size, (0, 0, 0, 0))
draw_samurai(fg, cx=552, base=708, s=0.95)
draw_text(fg, 'BR', 500, 768, 84)
# a few petals in the foreground
fd = ImageDraw.Draw(fg)
for (x, y, r, a) in [(700, 600, 13, 0.6), (420, 520, 10, 2.1), (760, 470, 9, 1.2)]:
    petal(fd, x * K, y * K, r * K, a, hexc('#FFC1D3', 240))

mono = Image.new('RGBA', bg.size, (0, 0, 0, 0))
draw_samurai(mono, cx=552, base=708, s=0.95, mono=True)
draw_text(mono, 'BR', 500, 768, 84, mono=True)

L = 432  # 108dp @ xxxhdpi
down(bg, L).save(os.path.join(OUT, 'bg.png'))
down(fg, L).save(os.path.join(OUT, 'fg.png'))
down(mono, L).save(os.path.join(OUT, 'mono.png'))

full = Image.alpha_composite(bg, fg)
full_s = down(full, 1000)
# previews: the 72dp safe square of 108 with circle and squircle masks
crop = full_s.crop((167, 167, 833, 833)).resize((400, 400), Image.LANCZOS)
prev = Image.new('RGBA', (860, 420), (30, 30, 30, 255))
circle = Image.new('L', (400, 400), 0)
ImageDraw.Draw(circle).ellipse([0, 0, 399, 399], fill=255)
squircle = Image.new('L', (400, 400), 0)
ImageDraw.Draw(squircle).rounded_rectangle([0, 0, 399, 399], radius=120, fill=255)
prev.paste(crop, (10, 10), circle)
prev.paste(crop, (450, 10), squircle)
prev.save(os.path.join(OUT, 'preview.png'))
full_s.save(os.path.join(OUT, 'full.png'))

# legacy rounded-square PNGs for old launchers
for name, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    icon = full_s.crop((167, 167, 833, 833)).resize((px, px), Image.LANCZOS)
    m = Image.new('L', (px, px), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, px - 1, px - 1], radius=int(px * 0.22), fill=255)
    out = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    out.paste(icon, (0, 0), m)
    out.save(os.path.join(OUT, f'legacy_{name}.png'))

# ---------------------------------------------------------------- TV banner (16:9)
K_save = K
bw, bh = 1778, 1000  # units; output 640x360
ban = draw_background(w=bw, h=bh, ox=60, oy=-40, scale=1.0)
draw_samurai(ban, cx=640, base=668, s=1.0)
draw_text(ban, 'AnimeVost', 1290, 400, 150, max_w=820)
draw_text(ban, 'BR', 1290, 590, 170)
fdb = ImageDraw.Draw(ban)
ban.resize((640, 360), Image.LANCZOS).save(os.path.join(OUT, 'banner.png'))
print('done')
