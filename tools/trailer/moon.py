import random, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops
random.seed(4); W, H = 256, 128
base = Image.effect_noise((W, H), 26).point(lambda v: 95 + (v - 128) * 0.5)
maria = Image.new('L', (W, H), 0); d = ImageDraw.Draw(maria)
for _ in range(9):
    x, y, r = random.randrange(W), random.randrange(30, 98), random.randrange(10, 26)
    d.ellipse((x - r * 1.5, y - r, x + r * 1.5, y + r), fill=70)
base = ImageChops.subtract(base, maria.filter(ImageFilter.GaussianBlur(5)))
d = ImageDraw.Draw(base)
for _ in range(70):
    x, y, r = random.randrange(W), random.randrange(8, 120), random.choice((1, 1, 2, 2, 3, 4, 6))
    d.ellipse((x - r - 1, y - r - 1, x + r + 1, y + r + 1), fill=112)
    d.ellipse((x - r, y - r, x + r, y + r), fill=72)
base.convert('RGB').save(sys.argv[1])
