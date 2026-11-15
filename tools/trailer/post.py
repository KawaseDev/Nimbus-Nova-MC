import sys, os, json, math
from multiprocessing import Pool
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
T = os.path.dirname(os.path.abspath(__file__)); W, H, FPS = 1920, 1080, 60
CAPS = [s['caption'] for s in json.load(open(T + '/manifest.json'))]
FD = '/System/Library/Fonts/Supplemental/'
WHITE, AMBER, CYAN = (255, 255, 255), (255, 200, 87), (95, 225, 240)
_f, _t = {}, {}
def font(bold, size):
    k = (bold, size)
    if k not in _f: _f[k] = ImageFont.truetype(FD + ('DIN Condensed Bold.ttf' if bold else 'DIN Alternate Bold.ttf'), size)
    return _f[k]
def label(text, bold, size, color, spacing=0, shadow=True):
    k = (text, bold, size, color, spacing)
    if k in _t: return _t[k]
    f = font(bold, size); widths = [f.getlength(c) + spacing for c in text]; pad = 24
    im = Image.new('RGBA', (int(sum(widths)) + pad * 2, int(size * 1.25) + pad * 2), (0, 0, 0, 0)); d = ImageDraw.Draw(im); x = pad
    for c, w in zip(text, widths): d.text((x, pad), c, font=f, fill=color + (255,)); x += w
    if shadow:
        sh = Image.new('RGBA', im.size, (0, 0, 0, 0)); sh.putalpha(im.getchannel('A').filter(ImageFilter.GaussianBlur(9)).point(lambda v: min(255, v * 2)))
        im = Image.alpha_composite(sh, im)
    _t[k] = im; return im
def put(layer, im, cx, cy, alpha=1.0, scale=1.0, anchor='c'):
    if alpha <= 0: return
    if scale != 1.0: im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.BILINEAR)
    if alpha < 1: im = im.copy(); im.putalpha(im.getchannel('A').point(lambda v: int(v * alpha)))
    x = cx - im.width // 2 if anchor == 'c' else cx - 24
    layer.alpha_composite(im, (max(0, int(x)), max(0, int(cy - im.height // 2)))) if 0 <= x and cy - im.height // 2 >= 0 else layer.paste(im, (int(x), int(cy - im.height // 2)), im)
def ramp(t, a, b): return min(max((t - a) / (b - a), 0), 1)
def ease(x): return 1 - (1 - min(max(x, 0), 1)) ** 3
def slam(t, t0): return 1 + 0.35 * (1 - ease((t - t0) / 0.12))
def gradient(top):
    g = Image.new('L', (1, H)); 
    for y in range(H):
        v = max(0, 1 - y / 190) if top else max(0, (y - (H - 400)) / 400)
        g.putpixel((0, y), int(215 * v ** 1.4))
    im = Image.new('RGBA', (W, H), (4, 7, 16, 255)); im.putalpha(g.resize((W, H))); return im
def vignette():
    v = Image.new('L', (192, 108))
    for y in range(108):
        for x in range(192):
            d = math.hypot((x - 96) / 96, (y - 54) / 54); v.putpixel((x, y), int(255 * (1 - 0.42 * max(0, d - 0.55) ** 1.5)))
    return v.resize((W, H), Image.BILINEAR).convert('RGB')
VIG = TOP = BOT = None
def frame(f):
    global VIG, TOP, BOT
    if VIG is None: VIG, TOP, BOT = vignette(), gradient(True), gradient(False)
    t = f / FPS; img = Image.open('%s/f3d/%04d.jpg' % (T, f)).convert('RGB'); shots = 12 <= t < 24
    i = min(int(t - 12), 11) if shots else -1; u = t - 12 - i
    if shots and u < 0.1:
        k = 1 + int(22 * (1 - u / 0.1) ** 1.5); img = img.resize((W // k, H), Image.BILINEAR).resize((W, H), Image.BILINEAR)
    glow = img.resize((480, 270), Image.BILINEAR).point(lambda v: int(max(0, v - 165) * (1.0 if shots else 1.7)))
    img = ImageChops.screen(img, glow.filter(ImageFilter.GaussianBlur(7)).resize((W, H), Image.BILINEAR))
    img = ImageChops.multiply(img, VIG)
    L = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    if t < 4:
        s = 'ARTEMIS IS TAKING HUMANS BACK TO THE MOON'; n = int(len(s) * ramp(t, 0.6, 1.9))
        if n: put(L, label(s[:n], False, 50, WHITE, 9), 110, 880, 1 - ramp(t, 3.5, 3.95), anchor='l')
    elif t < 8:
        put(L, label('WHO TRAINS', True, 210, WHITE, 6), 110, 760, ramp(t, 4.0, 4.05), slam(t, 4.0), 'l')
        if t >= 5.0: put(L, label('THE NEXT CREW?', True, 210, AMBER, 6), 110, 950, 1, slam(t, 5.0), 'l')
    elif t < 12:
        put(L, label('SPACE MISSION TRAINING', False, 46, CYAN, 14), W // 2, 800, ramp(t, 8.8, 9.2))
        put(L, label('BUILT IN MINECRAFT', True, 110, AMBER, 8), W // 2, 915, ramp(t, 10.0, 10.05), slam(t, 10.0))
    elif shots:
        L.alpha_composite(TOP); L.alpha_composite(BOT)
        put(L, label('NIMBUS NOVA', True, 46, WHITE, 6), 80, 70, 1, anchor='l')
        rec = label('IN-GAME FOOTAGE', False, 30, WHITE, 6); put(L, rec, W - 80 - rec.width // 2 + 24, 66)
        if int(t * 2) % 2 == 0: ImageDraw.Draw(L).ellipse((W - 80 - rec.width + 10, 55, W - 80 - rec.width + 30, 75), fill=(255, 70, 70, 255))
        put(L, label('%02d / 12' % (i + 1), False, 34, AMBER, 6), 84, 728, 1, anchor='l')
        put(L, label(CAPS[i], True, 230, WHITE, 4), 80, 940, 1, slam(u, 0.0), 'l')
        ImageDraw.Draw(L).rectangle((84, 1040, 84 + int(380 * u), 1046), fill=AMBER + (255,))
    elif t >= 24:
        L.alpha_composite(BOT)
        items = [(label(n, True, 104, AMBER, 4), label(w, True, 104, WHITE, 4)) for n, w in (('6', 'MISSIONS'), ('5', 'CREW ROLES'), ('REAL', 'NASA DATA'))]
        total = sum(n.width + w.width - 30 for n, w in items) + 2 * 70; x = (W - total) // 2
        for k, (n, w) in enumerate(items):
            t0 = 24.5 + k * 0.5; a = ramp(t, t0, t0 + 0.05); sc = slam(t, t0)
            put(L, n, x + n.width // 2, 900, a, sc); put(L, w, x + n.width - 30 + w.width // 2, 900, a, sc); x += n.width + w.width - 30 + 70
        put(L, label('NASA SPACE APPS CHALLENGE', False, 36, CYAN, 16), W // 2, 1010, ramp(t, 26.2, 26.7))
    img = Image.alpha_composite(img.convert('RGBA'), L).convert('RGB')
    hits = [(8, 0.9), (10, 0.25), (24, 0.9), (4, 0.25), (5, 0.25)] + [(12 + k, 0.4) for k in range(12)]
    for th, strength in hits:
        x = (t - th) / 0.16
        if 0 <= x < 1:
            off = int(14 * strength * (1 - x)) 
            if off:
                r, g, b = img.split(); img = Image.merge('RGB', (ImageChops.offset(r, off, 0), g, ImageChops.offset(b, -off, 0)))
            img = Image.blend(img, Image.new('RGB', (W, H), (235, 245, 255)), strength * (1 - x) ** 2)
    dark = max(1 - ramp(t, 0, 0.6), ramp(t, 7.7, 7.97) if t < 8 else 0, ramp(t, 27.9, 28.9))
    if dark > 0: img = Image.blend(img, Image.new('RGB', (W, H), (0, 0, 0)), dark)
    img.save('%s/out/%04d.jpg' % (T, f), quality=93)
if __name__ == '__main__':
    os.makedirs(T + '/out', exist_ok=True); fs = [int(a) for a in sys.argv[1:]] or [int(n[:4]) for n in sorted(os.listdir(T + '/f3d'))]
    with Pool(8) as p: p.map(frame, fs, chunksize=8)
