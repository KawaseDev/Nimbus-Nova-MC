import bpy, sys, json, math, random
from mathutils import Vector, Quaternion
S, START, END = sys.argv[sys.argv.index('--') + 1], int(sys.argv[-2]), int(sys.argv[-1])
shots = json.load(open(S + '/manifest.json'))
FPS, R_RING, SW, SH = 60, 17.0, 6.4, 3.6
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
try: sc.render.engine = 'BLENDER_EEVEE'
except TypeError: sc.render.engine = 'BLENDER_EEVEE_NEXT'
sc.eevee.taa_render_samples = 12
sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = 1920, 1080, 100
sc.view_settings.view_transform = 'Standard'
sc.render.image_settings.file_format = 'JPEG'; sc.render.image_settings.quality = 95
world = bpy.data.worlds.new('w'); sc.world = world; world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (0.0015, 0.002, 0.006, 1)
def emission(name, color, strength=1.0):
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; nt.nodes.clear()
    o = nt.nodes.new('ShaderNodeOutputMaterial'); e = nt.nodes.new('ShaderNodeEmission')
    e.inputs[0].default_value = (*color, 1); e.inputs[1].default_value = strength
    nt.links.new(e.outputs[0], o.inputs[0]); return m
def obj(name, data):
    o = bpy.data.objects.new(name, data); sc.collection.objects.link(o); return o
# moon
bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=10)
moon = bpy.context.object; bpy.ops.object.shade_smooth()
mm = bpy.data.materials.new('moon'); mm.use_nodes = True; nt = mm.node_tree
bsdf = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
tex = nt.nodes.new('ShaderNodeTexImage'); tex.image = bpy.data.images.load(S + '/moon.png'); tex.interpolation = 'Closest'
bump = nt.nodes.new('ShaderNodeBump'); bump.inputs['Strength'].default_value = 0.5; bump.inputs['Distance'].default_value = 0.6
nt.links.new(tex.outputs[0], bsdf.inputs['Base Color']); nt.links.new(tex.outputs[0], bump.inputs['Height'])
nt.links.new(bump.outputs[0], bsdf.inputs['Normal']); bsdf.inputs['Roughness'].default_value = 1.0
moon.data.materials.append(mm)
sun = obj('sun', bpy.data.lights.new('sun', 'SUN')); sun.data.energy = 5.5
sun.rotation_euler = (math.radians(62), 0, math.radians(58))
# stars
random.seed(11); verts, faces = [], []
for i in range(2600):
    v = Vector((random.gauss(0, 1), random.gauss(0, 1), random.gauss(0, 1))).normalized()
    a = v.orthogonal().normalized(); b = v.cross(a); s = random.choice((0.1, 0.13, 0.16, 0.2, 0.27))
    c = v * 300; verts += [c + a * s, c - a * s * 0.5 + b * s * 0.87, c - a * s * 0.5 - b * s * 0.87]; faces.append((3 * i, 3 * i + 1, 3 * i + 2))
me = bpy.data.meshes.new('stars'); me.from_pydata(verts, [], faces); obj('stars', me).data.materials.append(emission('star', (0.85, 0.92, 1.0), 1.6))
# screens
frame_mat = emission('frame', (0.37, 0.88, 0.94), 1.3); screens = []
nt = frame_mat.node_tree; e0 = next(n for n in nt.nodes if n.type == 'EMISSION'); o0 = next(n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL')
b0 = nt.nodes.new('ShaderNodeEmission'); b0.inputs[0].default_value = (0.02, 0.05, 0.08, 1); m0 = nt.nodes.new('ShaderNodeMixShader'); g0 = nt.nodes.new('ShaderNodeNewGeometry')
nt.links.new(g0.outputs['Backfacing'], m0.inputs[0]); nt.links.new(e0.outputs[0], m0.inputs[1]); nt.links.new(b0.outputs[0], m0.inputs[2]); nt.links.new(m0.outputs[0], o0.inputs[0])
def quad(w, h):
    m = bpy.data.meshes.new('q'); m.from_pydata([(-w / 2, -h / 2, 0), (w / 2, -h / 2, 0), (w / 2, h / 2, 0), (-w / 2, h / 2, 0)], [], [(0, 1, 2, 3)])
    uv = m.uv_layers.new(); 
    for loop, co in zip(uv.data, ((0, 0), (1, 0), (1, 1), (0, 1))): loop.uv = co
    return m
ring = obj('ring', None)
for i, shot in enumerate(shots):
    ang = math.radians(-90 + i * 30); out = Vector((math.cos(ang), math.sin(ang), 0))
    m = bpy.data.materials.new('s%d' % i); m.use_nodes = True; nt = m.node_tree; nt.nodes.clear()
    o = nt.nodes.new('ShaderNodeOutputMaterial'); e = nt.nodes.new('ShaderNodeEmission'); back = nt.nodes.new('ShaderNodeEmission')
    back.inputs[0].default_value = (0.02, 0.05, 0.08, 1); mix = nt.nodes.new('ShaderNodeMixShader'); geo = nt.nodes.new('ShaderNodeNewGeometry')
    t = nt.nodes.new('ShaderNodeTexImage'); t.interpolation = 'Closest'; t.image = bpy.data.images.load(shot['frames'][len(shot['frames']) // 2])
    nt.links.new(t.outputs[0], e.inputs[0]); nt.links.new(geo.outputs['Backfacing'], mix.inputs[0])
    nt.links.new(e.outputs[0], mix.inputs[1]); nt.links.new(back.outputs[0], mix.inputs[2]); nt.links.new(mix.outputs[0], o.inputs[0])
    p = obj('screen%d' % i, quad(SW, SH)); p.data.materials.append(m)
    q = (-out).to_track_quat('-Z', 'Z'); p.rotation_mode = 'QUATERNION'; p.rotation_quaternion = q; p.location = out * R_RING
    f = obj('frame%d' % i, quad(SW + 0.16, SH + 0.16)); f.data.materials.append(frame_mat)
    f.rotation_mode = 'QUATERNION'; f.rotation_quaternion = q; f.location = out * (R_RING - 0.03)
    p.parent = ring; f.parent = ring; screens.append((p, f, t, ang, shot))
# title
font = bpy.data.fonts.load('/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf')
cu = bpy.data.curves.new('title', 'FONT'); cu.body = 'NIMBUS NOVA'; cu.font = font; cu.align_x = 'CENTER'; cu.align_y = 'CENTER'
cu.extrude = 0.12; cu.bevel_depth = 0.012; cu.space_character = 1.12
title = obj('title', cu); title.rotation_mode = 'QUATERNION'; title.visible_shadow = False
tm = bpy.data.materials.new('title'); tm.use_nodes = True
b = next(n for n in tm.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
b.inputs['Base Color'].default_value = (0.9, 0.95, 1, 1); b.inputs['Emission Color'].default_value = (1, 1, 1, 1); b.inputs['Emission Strength'].default_value = 0.3
b.inputs['Roughness'].default_value = 0.35; cu.materials.append(tm)
key = obj('key', bpy.data.lights.new('key', 'POINT')); key.data.energy = 0
cam = obj('cam', bpy.data.cameras.new('cam')); cam.data.lens = 35; cam.data.clip_end = 1000; cam.rotation_mode = 'QUATERNION'; sc.camera = cam
def ease(x): x = min(max(x, 0), 1); return 1 - (1 - x) ** 3
def smooth(x): x = min(max(x, 0), 1); return x * x * (3 - 2 * x)
def look(pos, target, roll=0.0):
    cam.location = pos; cam.rotation_quaternion = (target - pos).to_track_quat('-Z', 'Z') @ Quaternion((0, 0, 1), roll)
def orbit(angle, radius, height): return Vector((math.cos(angle) * radius, math.sin(angle) * radius, height))
loaded = {}
def show(i, path):
    t = screens[i][2]
    if t.image.filepath == path: return
    old = t.image; t.image = bpy.data.images.load(path)
    if old.users == 0: bpy.data.images.remove(old)
def setup(f):
    t = f / FPS
    ring_on = t >= 12; title.hide_render = not (8 <= t < 12 or t >= 24); ring.rotation_euler = (0, 0, 0)
    for p, fr, *_ in screens: p.hide_render = fr.hide_render = not ring_on
    cam.data.lens = 35
    if t < 8:
        u = t / 8; look(orbit(math.radians(-90 - 10 + 14 * u), 62 - 30 * smooth(u) ** 1.5, 5 - 3 * u), Vector((0, 0, -3 + 5 * u)), math.radians(-4 + 4 * u))
    elif t < 12:
        u = (t - 8) / 4; a = math.radians(-90 - 16 + 30 * u)
        look(orbit(a, 46 - 9 * ease(u * 1.6) - 2 * u, 1.5), Vector((0, 0, 0.5)), math.radians(3 - 5 * u))
        title.location = orbit(math.radians(-90 - 16 + 30 * u) , 15, 0.6); title.rotation_quaternion = cam.rotation_quaternion
        s = 3.5 * (1.5 - 0.5 * ease(u / 0.09)); title.scale = (s,) * 3
    elif t < 23.6:
        i = min(int(t - 12), 11); u = t - 12 - i; ang = screens[i][3]; side = 1 if i % 2 else -1
        whip = (1 - ease(u / 0.22)) * math.radians(-24)
        a = ang + math.radians(-5 + 7 * u) * side * (1 - 0.6 * ease(u / 0.4)) + whip
        pos = orbit(a, R_RING + 9.6 - 3.1 * ease(u / 0.35) - 0.45 * u, side * 0.9 * (1 - ease(u / 0.5)))
        look(pos, orbit(ang, R_RING, 0), math.radians(side * 3.5 * (1 - ease(u / 0.4))))
        fr = screens[i][4]['frames']; show(i, fr[min(int(u * 60), len(fr) - 1)])
        if u > 0.75 and i < 11: show(i + 1, screens[i + 1][4]['frames'][0])
    else:
        u = (t - 23.6) / 5.4; k = ease(u / 0.28); ang = screens[11][3]
        ring.rotation_euler = (0, 0, math.radians(-14 * u))
        a = ang + math.radians(4 + 62 * k + 16 * u)
        look(orbit(a, R_RING + 6.2 + 27 * k + 3 * u, 0.2 + 12 * k), Vector((0, 0, 3.2 * k)), 0)
        for i, s in enumerate(screens): show(i, s[4]['frames'][int(f * 1.2 + i * 7) % len(s[4]['frames'])])
        fwd = cam.rotation_quaternion @ Vector((0, 0, -1)); up = cam.rotation_quaternion @ Vector((0, 1, 0))
        title.location = cam.location + fwd * 30 + up * 5.4; title.rotation_quaternion = cam.rotation_quaternion
        s = 2.9 * (1.6 - 0.6 * ease((t - 24) / 0.35)) if t >= 24 else 0.0001; title.scale = (s,) * 3
    key.location = cam.location; key.data.energy = 2200 if not title.hide_render else 0
for f in range(START, END):
    setup(f); sc.frame_set(f); sc.render.filepath = '%s/f3d/%04d.jpg' % (S, f); bpy.ops.render.render(write_still=True)
