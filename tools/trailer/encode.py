import bpy, sys, os
T, OUT = sys.argv[-2], sys.argv[-1]
bpy.ops.wm.read_factory_settings(use_empty=True); sc = bpy.context.scene
names = sorted(n for n in os.listdir(T + '/out') if n.endswith('.jpg'))
sc.render.fps = 60; sc.frame_start = 1; sc.frame_end = len(names)
sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = 1920, 1080, 100
sc.view_settings.view_transform = 'Standard'
se = sc.sequence_editor_create(); strips = se.strips if hasattr(se, 'strips') else se.sequences
v = strips.new_image('v', T + '/out/' + names[0], 1, 1)
for n in names[1:]: v.elements.append(n)
strips.new_sound('a', T + '/track.wav', 2, 1)
try: sc.render.image_settings.media_type = 'VIDEO'
except Exception as e: print('media_type', e)
sc.render.image_settings.file_format = 'FFMPEG'
ff = sc.render.ffmpeg; ff.format = 'MPEG4'; ff.codec = 'H264'; ff.constant_rate_factor = sys.argv[-3]; ff.ffmpeg_preset = 'GOOD'; ff.gopsize = 60
ff.audio_codec = 'AAC'; ff.audio_bitrate = 192; ff.audio_mixrate = 44100 if hasattr(ff, 'audio_mixrate') else None
sc.render.filepath = OUT; bpy.ops.render.render(animation=True)
