import numpy as np, wave, sys
S = sys.argv[sys.argv.index('--') + 1]
sr = 44100; B = 0.5; T = 29.0; N = int(sr * T)
rng = np.random.default_rng(7)
out = np.zeros((2, N)); send = np.zeros((2, N))
def filt(x, lo=None, hi=None, order=4):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / sr) + 1e-9
    if hi: X = X / np.sqrt(1 + (f / hi) ** (2 * order))
    if lo: X = X / np.sqrt(1 + (lo / f) ** (2 * order))
    return np.fft.irfft(X, len(x))
def add(buf, x, t, gain=1.0, pan=0.0):
    i = int(t * sr)
    if i >= N: return
    x = x[:N - i]
    buf[0, i:i + len(x)] += x * gain * (1 - max(pan, 0)); buf[1, i:i + len(x)] += x * gain * (1 + min(pan, 0))
def tt(d): return np.arange(int(d * sr)) / sr
def hz(m): return 440 * 2 ** ((m - 69) / 12)
def saw(f, t, ph=0.0): return 2 * ((t * f + ph) % 1) - 1
def kick():
    t = tt(0.45); f = 42 + 110 * np.exp(-t * 28)
    return np.tanh(2.2 * np.sin(2 * np.pi * np.cumsum(f) / sr) * np.exp(-t * 7)) + 0.5 * np.exp(-t * 400) * rng.standard_normal(len(t)) * 0.3
def clap():
    t = tt(0.3); n = filt(rng.standard_normal(len(t)), 900, 7000, 2)
    return n * (np.exp(-t * 22) + 0.6 * np.exp(-((t - 0.012) % 0.011) * 300) * (t < 0.04))
def hat(d=0.05):
    t = tt(d * 4); return filt(rng.standard_normal(len(t)), 7000, None, 3) * np.exp(-t / d * 3)
def boom(d=3.5):
    t = tt(d); f = 34 + 60 * np.exp(-t * 6)
    s = np.tanh(3 * np.sin(2 * np.pi * np.cumsum(f) / sr) * np.exp(-t * 1.6))
    return s + filt(rng.standard_normal(len(t)), 40, 2500, 2) * np.exp(-t * 3.5) * 1.2
def riser(d):
    t = tt(d); f = 180 * (12 ** (t / d))
    s = np.sin(2 * np.pi * np.cumsum(f) / sr) * 0.25 + filt(rng.standard_normal(len(t)), 1500, None, 2) * 0.5
    return s * (t / d) ** 2.5
def pad(notes, d, cut):
    t = tt(d); x = np.zeros((2, len(t)))
    for m in notes:
        for k, det in enumerate((-0.11, -0.04, 0.03, 0.1)):
            x[k % 2] += saw(hz(m + det), t, rng.random())
    e = np.minimum(t / 0.6, 1) * np.minimum((d - t) / 0.5, 1)
    return np.stack([filt(x[0], None, cut, 2), filt(x[1], None, cut, 2)]) * e / len(notes)
def pluck(m, d=0.22, cut=3200):
    t = tt(d); x = saw(hz(m), t) + 0.6 * np.sign(saw(hz(m) * 1.003, t))
    return filt(x, None, cut, 2) * np.exp(-t * 16)
def bass(m, d):
    t = tt(d); x = np.sin(2 * np.pi * hz(m) * t) + 0.35 * filt(saw(hz(m + 12), t), None, 700, 2)
    return np.tanh(1.8 * x) * np.minimum(t / 0.005, 1) * np.exp(-t * 5)
K, C, H = kick(), clap(), hat()
# chords: (root midi, triad) per 4 s block
Dm, Bb, F, Cm = (38, (50, 53, 57, 62)), (34, (50, 53, 58, 62)), (41, (48, 53, 57, 60)), (36, (48, 52, 55, 60))
blocks = [(0, 8, Dm, 520), (8, 4, Dm, 1500), (12, 4, Bb, 2000), (16, 4, F, 2000), (20, 4, Cm, 2200), (24, 5, Dm, 1700)]
pump = np.ones(N); tn = np.arange(N) / sr
drop = (tn >= 8) & (tn < 24)
pump[drop] = 1 - 0.75 * np.exp(-((tn[drop] % B) / B) * 5)
padbuf = np.zeros((2, N))
for start, d, (root, notes), cut in blocks:
    p = pad(notes, d + 0.4, cut); i = int(start * sr); n = min(p.shape[1], N - i)
    padbuf[:, i:i + n] += p[:, :n] * (0.5 if start < 8 else 0.42)
    if start < 8:
        add(out, np.sin(2 * np.pi * hz(root - 12) * tt(d)) * np.minimum(tt(d) / 2, 1), start, 0.35)
        continue
    arp = [notes[0], notes[2], notes[1] + 12, notes[2], notes[3], notes[2], notes[1] + 12, notes[2] + 12]
    for s in range(int(d / (B / 4))):
        t0 = start + s * B / 4
        if start >= 24 and s % 2: continue
        g = (0.2 if 8 <= start < 12 else 0.3) * (1 if start < 24 else max(0, 1 - (t0 - 24) / 4))
        pl = pluck(arp[s % 8] + (12 if s % 16 >= 8 else 0))
        add(out, pl, t0, g, 0.35 * (-1) ** s); add(send, pl, t0, g * 0.5, -0.35 * (-1) ** s)
    if start < 24:
        for s in range(int(d / (B / 2))):
            if 23 <= start + s * B / 2: break
            add(out, bass(root if s % 4 != 3 else root + 12, B / 2), start + s * B / 2, 0.55 * (0.4 if s % 2 == 0 else 1))
out += padbuf * pump; send += padbuf * pump * 0.5
# intro ticks, heartbeat, sparse plucks
for b in range(16):
    t0 = b * B
    add(out, hat(0.02), t0, 0.12 + 0.01 * b, 0.4 * (-1) ** b)
    if b % 4 == 0 or (b >= 12 and b % 2 == 0) or b >= 14: add(out, K, t0, 0.45 + 0.03 * b)
    if b % 4 == 2:
        pl = pluck([74, 77, 81, 86][b // 4], 0.9, 1800); add(out, pl, t0, 0.3); add(send, pl, t0, 0.5)
for s in range(8): add(out, C, 7 + s * B / 4, 0.12 + 0.05 * s)
add(out, riser(2.0), 5.95, 0.5); add(send, riser(2.0), 5.95, 0.3)
# drop drums
for b in range(16, 48):
    t0 = b * B
    if t0 >= 23: break
    add(out, K, t0, 0.95)
    if t0 >= 12:
        if b % 2 == 1: add(out, C, t0, 0.5); add(send, C, t0, 0.35)
        add(out, H, t0 + B / 2, 0.3, 0.2)
        for s in (1, 3): add(out, hat(0.02), t0 + s * B / 4, 0.12, -0.3)
    elif b % 4 == 2: add(send, C, t0, 0.5)
for s in range(8): add(out, C, 23 + s * B / 4, 0.15 + 0.06 * s); add(out, K, 23 + s * B / 4, 0.3 + 0.07 * s)
add(out, riser(2.0), 21.95, 0.4)
rev = boom(1.2)[::-1] * np.linspace(0, 1, len(boom(1.2))) ** 3
for t0, g in ((8, 1.0), (12, 0.7), (16, 0.45), (20, 0.45), (24, 1.1)):
    add(out, boom(), t0, g * 0.8); add(send, boom(), t0, g * 0.5)
    add(out, filt(rev, 300, None, 2), t0 - 1.2, 0.25 * g)
# reverb
ir_t = tt(2.2)
for c in range(2):
    ir = filt(rng.standard_normal(len(ir_t)), 200, 6000, 1) * np.exp(-ir_t * 2.6)
    n = N + len(ir) - 1
    wet = np.fft.irfft(np.fft.rfft(send[c], n) * np.fft.rfft(ir, n), n)[:N]
    out[c] += wet * 0.02
out = np.tanh(out * 0.9)
out /= np.abs(out).max() / 0.89
fade = np.clip((T - tn) / 2.0, 0, 1) ** 1.5 * np.clip(tn / 0.05, 0, 1)
pcm = (out * fade * 32767).T.astype('<i2')
with wave.open(S + '/track.wav', 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())
rms = [float(np.sqrt((out[:, int(a * sr):int(b * sr)] ** 2).mean())) for a, b in ((0, 8), (8, 12), (12, 24), (24, 29))]
print('rms', [round(r, 3) for r in rms], 'peak', float(np.abs(out).max()))
