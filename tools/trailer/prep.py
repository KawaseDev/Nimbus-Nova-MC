import glob, json, os, sys
from multiprocessing import Pool
from PIL import Image
T = os.path.dirname(os.path.abspath(__file__)); SRC = sys.argv[1]
SHOTS = [('clip-dome-exterior', 'MARS'), ('play-power', 'REPAIR'), ('play-oxygen', 'BREATHE'), ('clip-aster-rover', 'DRIVE'),
         ('play-pilot-docking', 'DOCK'), ('play-pilot-navigation', 'NAVIGATE'), ('play-comms-docking', 'TRANSMIT'), ('play-pilot-descent', 'LAND'),
         ('clip-moon-surface', 'EXPLORE'), ('play-science-preparation', 'ANALYSE'), ('clip-scenario-greenhouse-overview', 'GROW'), ('clip-scenario-shelter-overview', 'SURVIVE')]
def shot(i):
    name, _ = SHOTS[i]; fs = sorted(glob.glob('%s/%s/*.png' % (SRC, name))); n = min(len(fs), 96 if name.startswith('clip') else 120) - 1
    os.makedirs('%s/shots/%02d' % (T, i), exist_ok=True); out = []
    for k in range(60):
        a = int(k * n / 60); im = Image.open(fs[a]).convert('RGB')
        if name.startswith('play'): im = im.crop((330, 186, 1590, 895)).resize((1920, 1080), Image.LANCZOS)
        else: im = Image.blend(im, Image.open(fs[min(a + 1, len(fs) - 1)]).convert('RGB'), 0.5)
        p = '%s/shots/%02d/%02d.jpg' % (T, i, k); im.save(p, quality=93); out.append(p)
    return out
if __name__ == '__main__':
    with Pool(6) as p: frames = p.map(shot, range(12))
    json.dump([{'caption': c, 'frames': f} for (_, c), f in zip(SHOTS, frames)], open(T + '/manifest.json', 'w'))
