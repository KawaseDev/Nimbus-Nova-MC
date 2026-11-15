import glob, os
from PIL import Image
SRC='/Users/kawase/Documents/Github/Nimbus-Nova-MC/build/run/clientGameTest/screenshots'
def shot(pat, out, aspect):
    f=sorted(glob.glob(f'{SRC}/*{pat}*'))[0]
    im=Image.open(f).convert('RGB'); im=im.resize((im.width*2, im.height*2), Image.NEAREST)
    w,h=im.size
    if w/h>aspect: nw=int(h*aspect); im=im.crop(((w-nw)//2,0,(w-nw)//2+nw,h))
    else: nh=int(w/aspect); im=im.crop((0,(h-nh)//2,w,(h-nh)//2+nh))
    im.save(f'img/{out}.png'); print(out, os.path.basename(f), im.size)
def user(src, out, aspect=None):
    im=Image.open(f'um/ppt/media/{src}').convert('RGB'); w,h=im.size
    if aspect:
        if w/h>aspect: nw=int(h*aspect); im=im.crop(((w-nw)//2,0,(w-nw)//2+nw,h))
        else: nh=int(w/aspect); im=im.crop((0,(h-nh)//2,w,(h-nh)//2+nh))
    im.save(f'img/{out}.png'); print(out, im.size)
shot('dome-exterior','title',5.4/5.625)
shot('fullscreen-mission-hub','step1',16/9); shot('join-session','step2',16/9)
shot('ship-emergency-loads','step3',16/9); shot('0052_mission-debrief','step4',16/9)
shot('mission-hud','storm',16/9); shot('mission-science','science',16/9) if glob.glob(f'{SRC}/*mission-science*') else shot('mars-dust-storm','science',16/9)
shot('fullscreen-momentum-docking','nova',2.0); shot('moon-surface-overview','moon',2.0); shot('scenario-greenhouse-overview','green',2.0)
user('image3.jpeg','whiteboard'); user('image10.jpeg','artemis',2.0); user('image12.jpeg','mcedu'); user('image13.jpeg','logo',1.0)
