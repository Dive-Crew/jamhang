"""잠항 사운드 팩 — 전부 코드로 합성한 원본 음원 (외부 샘플 미사용)."""
import numpy as np, scipy.signal as sg, soundfile as sf, os, sys

SR = 48000
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/snd/out"
os.makedirs(OUT, exist_ok=True)
TAU = 2 * np.pi


def db(x):
    return 10 ** (x / 20)


def rms(x):
    return np.sqrt(np.mean(x ** 2) + 1e-20)


def edge(f, fc, w=1.0):
    x = np.clip(np.log2(np.maximum(f, 1e-9) / fc) / w + 0.5, 0, 1)
    return 0.5 - 0.5 * np.cos(np.pi * x)


def pnoise(n, lo, hi, slope, rng, w=1.0):
    """주기적(끝과 처음이 이어지는) 대역 제한 노이즈. 전력 ~ 1/f^slope"""
    f = np.fft.rfftfreq(n, 1 / SR)
    mag = np.zeros_like(f)
    m = f > 0
    mag[m] = f[m] ** (-slope / 2)
    mag *= edge(f, lo, w) * (1 - edge(f, hi, w))
    ph = rng.uniform(0, TAU, len(f))
    x = np.fft.irfft(mag * np.exp(1j * ph), n)
    return x / rms(x)


def clp(x, fc, order=2):
    """원형(주기 유지) 저역 통과"""
    f = np.fft.rfftfreq(x.shape[-1], 1 / SR)
    H = 1 / np.sqrt(1 + (f / fc) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x, axis=-1) * H, x.shape[-1], axis=-1)


def chp(x, fc, order=2):
    f = np.fft.rfftfreq(x.shape[-1], 1 / SR)
    r = (f / fc) ** (2 * order)
    H = np.sqrt(r / (1 + r))
    return np.fft.irfft(np.fft.rfft(x, axis=-1) * H, x.shape[-1], axis=-1)


def lfo(n, comps, lo=0.0, hi=1.0):
    """comps: [(루프당 주기 수, 진폭, 위상)] → lo..hi 로 정규화한 주기적 LFO"""
    t = np.arange(n) / n
    y = sum(a * np.sin(TAU * (k * t) + p) for k, a, p in comps)
    y = (y - y.min()) / (y.max() - y.min() + 1e-12)
    return lo + (hi - lo) * y


def reverb_ir(rt, rng, lp=4000, pre=0.012):
    n = int(SR * rt * 1.4)
    t = np.arange(n) / SR
    ir = rng.standard_normal((2, n)) * np.exp(-6.9 * t / rt)
    ir = clp(ir, lp)
    ir[:, : int(pre * SR)] *= 0
    return ir / np.sqrt((ir ** 2).sum(axis=1, keepdims=True))


def creverb(x, ir, wet):
    """원형 컨볼루션 리버브 (루프용). x: (2,n)"""
    n = x.shape[1]
    irp = np.zeros((2, n))
    irp[:, : min(n, ir.shape[1])] = ir[:, :n]
    y = np.fft.irfft(np.fft.rfft(x, axis=1) * np.fft.rfft(irp, axis=1), n, axis=1)
    return x + wet * y


def reverb(x, ir, wet, tail=True):
    y = np.stack([sg.fftconvolve(x[c], ir[c]) for c in range(2)])
    if tail:
        out = np.zeros_like(y)
        out[:, : x.shape[1]] = x
    else:
        y = y[:, : x.shape[1]]
        out = x.copy()
    return out + wet * y


def pan(x, p):
    """p: -1(L)..1(R)"""
    a = (p + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)])


def bubble(f0, f1, dur, rng):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** (t / dur)
    ph = TAU * np.cumsum(f) / SR
    env = np.minimum(t / 0.004, 1) * np.exp(-t / (dur * 0.33))
    return np.sin(ph) * env


def add_at(buf, x, start, wrap=True):
    """buf (2,n) 에 x (2,m) 를 start 샘플부터 더함"""
    n = buf.shape[1]
    idx = start + np.arange(x.shape[1])
    if wrap:
        idx %= n
        np.add.at(buf, (slice(None), idx), x)
    else:
        ok = idx < n
        buf[:, idx[ok]] += x[:, ok]


def bell(f, dur, tau=0.8, partials=((1, 1, 1), (2.0, 0.30, 0.6), (3.01, 0.12, 0.4), (4.17, 0.05, 0.3)), att=0.008):
    n = int(dur * SR)
    t = np.arange(n) / SR
    y = sum(a * np.sin(TAU * f * r * t) * np.exp(-t / (tau * d)) for r, a, d in partials)
    y *= 0.5 - 0.5 * np.cos(np.pi * np.minimum(t / att, 1))
    return y


def fade(x, fin=0.0, fout=0.0):
    x = x.copy()
    n = x.shape[-1]
    if fin > 0:
        k = int(fin * SR)
        x[..., :k] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(k) / k)
    if fout > 0:
        k = int(fout * SR)
        x[..., n - k:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(k) / k)
    return x


def norm(x, target_rms=None, peak=None):
    if target_rms is not None:
        x = x * db(target_rms) / rms(x)
    if peak is not None:
        pk = np.abs(x).max()
        if target_rms is None or pk > db(peak):
            x = x * db(peak) / pk
    return x


def save(name, x):
    x = np.clip(x, -1, 1)
    sf.write(f"{OUT}/{name}.wav", x.T, SR, subtype="PCM_24")
    print(f"{name:28s} {x.shape[1]/SR:6.2f}s  rms {20*np.log10(rms(x)):6.1f} dB  peak {20*np.log10(np.abs(x).max()):6.1f} dB")


# ---------------------------------------------------------------- 선실 배경음
L = 64  # 초. 8초 호흡 주기의 8배 → 호흡 안내와 박자가 어긋나지 않음
N = L * SR
T = np.arange(N) / SR


def qf(f):
    """루프 길이에 맞게 주파수를 양자화(이음매에서 위상이 정확히 이어짐)"""
    return round(f * L) / L


def wash(rng, lo, hi, slope, lf_comps, depth, width=0.6):
    c = pnoise(N, lo, hi, slope, rng)
    out = []
    for ch in range(2):
        own = pnoise(N, lo, hi, slope, rng)
        ph = rng.uniform(0, TAU)
        env = lfo(N, [(k, a, p + ph * 0.15) for k, a, p in lf_comps], 1 - depth, 1.0)
        out.append(((1 - width) * c + width * own) * env)
    return np.stack(out)


def pad(rng, notes, swell_ks, bright=1.0):
    """notes: [(Hz, 진폭)] — 느리게 숨 쉬는 사인 패드"""
    y = np.zeros((2, N))
    for i, (f, a) in enumerate(notes):
        k = swell_ks[i % len(swell_ks)]
        env = lfo(N, [(k, 1, rng.uniform(0, TAU)), (k * 2 + 1, 0.3, rng.uniform(0, TAU))], 0.15, 1.0) ** 1.5
        for det, p in ((0, -0.5), (rng.integers(3, 9) / L, 0.5)):
            fq = qf(f) + det
            ph = rng.uniform(0, TAU)
            tone = np.sin(TAU * fq * T + ph) + 0.12 * bright * np.sin(TAU * 2 * fq * T + ph * 1.7)
            y += pan(tone * env * a * 0.5, p)
    return y


def bubbles(rng, count, frange, drange, amp, cluster=1):
    y = np.zeros((2, N))
    for _ in range(count):
        t0 = rng.uniform(0, L)
        for j in range(rng.integers(1, cluster + 1)):
            f0 = rng.uniform(*frange)
            b = bubble(f0, f0 * rng.uniform(1.25, 1.9), rng.uniform(*drange), rng) * amp * rng.uniform(0.3, 1.0)
            add_at(y, pan(b, rng.uniform(-0.8, 0.8)), int((t0 + j * rng.uniform(0.05, 0.22)) * SR))
    return y


def cabin_light_sea():
    """빛의 바다 — 얕고 밝은 물결. 8초 주기로 살짝 부풀어 호흡 안내와 같은 박자."""
    rng = np.random.default_rng(11)
    w = wash(rng, 250, 3200, 1.6, [(8, 1, 0), (5, 0.6, 1.3), (3, 0.5, 2.1)], 0.55)
    air = wash(rng, 2500, 9000, 1.0, [(8, 1, 0.6), (11, 0.5, 0.2)], 0.7) * 0.10
    rum = np.stack([pnoise(N, 45, 110, 1.0, rng)] * 2) * 0.05
    p = pad(rng, [(146.83, 0.9), (220.0, 0.8), (293.66, 0.7), (369.99, 0.45), (440.0, 0.35), (659.25, 0.16)], [3, 4, 5, 7])
    b = clp(bubbles(rng, 26, (700, 1700), (0.05, 0.11), 0.5, cluster=3), 3500)
    x = 0.55 * w + air + rum + 0.20 * p + 0.10 * b
    return creverb(x, reverb_ir(3.0, rng, 5000), 0.35)


def cabin_kelp_forest():
    """해초 숲 — 16초 주기로 천천히 흔들리는 조류, 낮게 깔린 물방울."""
    rng = np.random.default_rng(22)
    w = wash(rng, 180, 1500, 1.7, [(4, 1, 0), (7, 0.45, 0.9), (3, 0.4, 2.4)], 0.65, width=0.7)
    sway = wash(rng, 500, 2400, 1.4, [(4, 1, 1.5), (9, 0.4, 0.3)], 0.85, width=0.8) * 0.22
    rum = np.stack([pnoise(N, 45, 110, 1.0, rng)] * 2) * 0.06
    p = pad(rng, [(87.31, 1.0), (130.81, 0.9), (220.0, 0.6), (261.63, 0.4), (329.63, 0.28)], [2, 3, 5], bright=0.5)
    b = clp(bubbles(rng, 34, (380, 950), (0.07, 0.16), 0.5, cluster=5), 2800)
    x = 0.6 * w + sway + rum + 0.22 * p + 0.14 * b
    return creverb(x, reverb_ir(3.5, rng, 3500), 0.4)


def cabin_old_gate():
    """오래된 문 — 깊고 어두운 울림. 멀리서 아주 드물게 낮은 금속성 공명."""
    rng = np.random.default_rng(33)
    w = wash(rng, 110, 1300, 1.7, [(2, 1, 0), (3, 0.6, 1.1), (5, 0.3, 0.4)], 0.5, width=0.7)
    rum = np.stack([pnoise(N, 40, 100, 0.8, rng)] * 2) * 0.10
    p = pad(rng, [(65.41, 1.0), (98.0, 0.8), (130.81, 0.55), (196.0, 0.3), (155.56, 0.2), (261.63, 0.3), (392.0, 0.15)], [2, 3], bright=0.3)
    res = np.zeros((2, N))
    for t0, f in ((6.0, 110.0), (27.5, 82.4), (47.0, 123.5)):
        g = bell(f, 9.0, tau=3.2, partials=((1, 1, 1), (1.51, 0.6, 0.8), (2.33, 0.35, 0.6), (3.7, 0.12, 0.4)), att=0.6)
        add_at(res, pan(g, rng.uniform(-0.6, 0.6)), int(t0 * SR))
    res = clp(res, 1100)
    x = 0.55 * w + rum + 0.26 * p + 0.06 * res
    return creverb(x, reverb_ir(5.0, rng, 2200), 0.55)


def cabin_dragon_palace():
    """용궁 — 물속으로 번지는 유리 종소리(5음 음계)와 넓은 패드."""
    rng = np.random.default_rng(44)
    w = wash(rng, 180, 2400, 1.8, [(4, 1, 0), (5, 0.6, 2.0), (3, 0.4, 0.7)], 0.55, width=0.7)
    rum = np.stack([pnoise(N, 45, 110, 1.0, rng)] * 2) * 0.05
    p = pad(rng, [(110.0, 1.0), (164.81, 0.85), (220.0, 0.7), (277.18, 0.5), (329.63, 0.4), (493.88, 0.18)], [3, 4, 5, 7], bright=0.8)
    scale = [440.0, 493.88, 554.37, 659.25, 739.99, 880.0]
    ch = np.zeros((2, N))
    t0 = 2.0
    while t0 < L - 1:
        f = scale[rng.integers(len(scale))] * (0.5 if rng.random() < 0.3 else 1)
        g = bell(f, 6.0, tau=1.6, partials=((1, 1, 1), (2.76, 0.22, 0.45), (5.4, 0.06, 0.25)), att=0.03)
        add_at(ch, pan(g * rng.uniform(0.5, 1.0), rng.uniform(-0.7, 0.7)), int(t0 * SR))
        t0 += rng.uniform(5.5, 10.5)
    ch = clp(ch, 2600)
    x = 0.5 * w + rum + 0.24 * p + 0.07 * ch
    return creverb(x, reverb_ir(5.5, rng, 4000), 0.6)


# ---------------------------------------------------------------- 효과음
def band_stack(n, rng):
    cs = [250, 500, 1000, 2000, 4000, 8000]
    return cs, [pnoise(n, c / 1.5, c * 1.5, 1.2, rng, w=0.7) for c in cs]


def sfx_hatch_close():
    """해치 닫힘 (03 전환, 약 3초): 휠이 감기고 → 쿵 닫히고 → 압력이 잠기는 숨소리."""
    rng = np.random.default_rng(101)
    n = int(3.3 * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    tc, gap = 0.05, 0.075
    while tc < 1.7:  # 래칫: 간격이 점점 벌어짐
        k = int(0.09 * SR)
        tt = np.arange(k) / SR
        click = pnoise(k, 1200, 4500, 1.0, rng) * np.exp(-tt / 0.006) * 0.5
        click += (np.sin(TAU * 520 * tt) + 0.6 * np.sin(TAU * 790 * tt)) * np.exp(-tt / 0.03) * 0.35
        s = int(tc * SR)
        y[s:s + k] += click * (0.55 + 0.45 * tc / 1.7)
        tc += gap
        gap *= 1.13
    rub = pnoise(n, 180, 900, 1.5, rng) * np.clip(t / 0.3, 0, 1) * np.clip((1.8 - t) / 0.25, 0, 1) * 0.10
    td = np.clip(t - 1.85, 0, None)
    on = (t >= 1.85)
    thud = np.sin(TAU * (48 * td + (90 - 48) * 0.12 * (1 - np.exp(-td / 0.12)))) * np.exp(-td / 0.2) * on * 0.9
    thud += pnoise(n, 60, 320, 1.0, rng) * np.exp(-td / 0.07) * on * 0.5
    clunk = sum(a * np.sin(TAU * f * td) * np.exp(-td / d) for f, a, d in ((182, 0.35, 0.3), (297, 0.22, 0.22), (463, 0.12, 0.15))) * on
    ts = np.clip(t - 1.98, 0, None)
    hiss = pnoise(n, 1800, 9000, 0.8, rng) * np.minimum(ts / 0.05, 1) * np.exp(-ts / 0.42) * (t >= 1.98) * 0.16
    x = np.stack([y + rub + thud + clunk + hiss] * 2)
    x[1] = np.roll(x[1], 9)
    x = reverb(x, reverb_ir(0.9, rng, 3000), 0.28)[:, : int(3.9 * SR)]
    return fade(x, 0.005, 0.5)


def sfx_dive_start():
    """잠항 시작: 물이 덮이며 소리가 먹먹해짐. 끝은 배경음으로 자연스럽게 넘어가도록 사라짐."""
    rng = np.random.default_rng(102)
    n = int(5.0 * SR)
    t = np.arange(n) / SR
    cs, bands = band_stack(n, rng)
    y = np.zeros(n)
    for i, (c, b) in enumerate(zip(cs, bands)):
        t_die = 3.6 - i * 0.55  # 높은 대역일수록 먼저 닫힘
        g = np.clip((t_die - t) / 1.1, 0, 1) if i >= 1 else np.ones(n)
        y += b * g * (0.9 if i < 3 else 0.6)
    y *= np.clip(t / 0.5, 0, 1) ** 2 * 0.2
    f = 196 * (98 / 196) ** np.clip(t / 3.5, 0, 1)
    tone = np.sin(TAU * np.cumsum(f) / SR) * np.clip(t / 0.6, 0, 1) * 0.16
    x = np.stack([y + tone, np.roll(y, 211) + tone])
    for _ in range(46):
        f0 = rng.uniform(450, 1700)
        b = bubble(f0, f0 * rng.uniform(1.3, 2.0), rng.uniform(0.05, 0.13), rng) * rng.uniform(0.05, 0.17)
        add_at(x, pan(b, rng.uniform(-0.9, 0.9)), int(rng.uniform(0.25, 3.2) * SR), wrap=False)
    x = reverb(x, reverb_ir(2.2, rng, 3500), 0.35, tail=False)
    return fade(x, 0.02, 1.8)


def sfx_surface_complete():
    """부상 완료 (07 결과): 먹먹함이 열리고 수면 위로 — 부드러운 두 음."""
    rng = np.random.default_rng(103)
    n = int(4.2 * SR)
    t = np.arange(n) / SR
    cs, bands = band_stack(n, rng)
    y = np.zeros(n)
    for i, (c, b) in enumerate(zip(cs, bands)):
        g = np.clip((t - (0.2 + i * 0.4)) / 1.0, 0, 1) if i >= 1 else np.ones(n)
        y += b * g * (0.9 if i < 3 else 0.55)
    y *= np.clip(t / 0.8, 0, 1) * np.clip((3.3 - t) / 1.0, 0, 1) * 0.16
    f = 110 * 2 ** np.clip(t / 2.6, 0, 1)
    tone = np.sin(TAU * np.cumsum(f) / SR) * np.clip(t / 0.8, 0, 1) * np.clip((2.9 - t) / 0.5, 0, 1) * 0.13
    ts = np.clip(t - 2.55, 0, None)
    splash = pnoise(n, 900, 7000, 1.0, rng) * np.minimum(ts / 0.06, 1) * np.exp(-ts / 0.4) * (t >= 2.55) * 0.12
    x = np.stack([y + tone + splash, np.roll(y, 173) + tone + np.roll(splash, 31)])
    full = np.zeros((2, int(6.5 * SR)))
    full[:, :n] = x
    for t0, fq, a in ((2.85, 587.33, 0.30), (3.22, 880.0, 0.24)):
        g = bell(fq, 3.0, tau=0.9)
        add_at(full, pan(g * a, 0.0), int(t0 * SR), wrap=False)
    full = reverb(full, reverb_ir(2.4, rng, 5000), 0.3, tail=False)
    return fade(full, 0.02, 1.2)


def chime(notes, dur, tau, rng_seed, wet=0.22, lp=5000):
    rng = np.random.default_rng(rng_seed)
    x = np.zeros((2, int(dur * SR)))
    for t0, fq, a in notes:
        add_at(x, pan(bell(fq, dur - t0 - 0.01, tau=tau) * a, 0.0), int(t0 * SR), wrap=False)
    x = clp(x, lp)
    x = reverb(x, reverb_ir(1.6, rng, 4500), wet, tail=False)
    return fade(x, 0.003, min(0.5, dur * 0.3))


def sfx_sonar_ping():
    rng = np.random.default_rng(107)
    n = int(3.2 * SR)
    x = np.zeros((2, n))
    for t0, a, p in ((0.0, 0.5, 0.0), (0.42, 0.17, -0.5), (0.84, 0.06, 0.5)):
        g = bell(1046.5, 1.6, tau=0.33, partials=((1, 1, 1), (2.0, 0.08, 0.5)), att=0.003)
        add_at(x, pan(g * a, p), int(t0 * SR), wrap=False)
    x = reverb(x, reverb_ir(2.5, rng, 3500), 0.4, tail=False)
    return fade(x, 0.002, 0.8)


def sfx_ui(kind):
    rng = np.random.default_rng(110)
    if kind == "tap":
        b = bubble(700, 1150, 0.09, rng) * 0.45
        x = np.zeros((2, int(0.16 * SR)))
        add_at(x, pan(b, 0), 0, wrap=False)
    else:  # select
        x = np.zeros((2, int(0.4 * SR)))
        add_at(x, pan(bubble(800, 1300, 0.08, rng) * 0.4, 0), 0, wrap=False)
        add_at(x, pan(bell(880, 0.35, tau=0.11) * 0.22, 0), int(0.03 * SR), wrap=False)
    return fade(clp(x, 5000), 0.001, 0.05)


def sfx_layer_added():
    """지층 +1겹: 낮게 내려앉는 소리 뒤에 작은 세 음."""
    rng = np.random.default_rng(111)
    n = int(2.4 * SR)
    t = np.arange(n) / SR
    thud = np.sin(TAU * (70 * t + 40 * 0.1 * (1 - np.exp(-t / 0.1)))) * np.exp(-t / 0.16) * 0.55
    brush = pnoise(n, 350, 2200, 1.4, rng) * np.sin(np.pi * np.clip(t / 0.7, 0, 1)) ** 2 * 0.07
    x = np.stack([thud + brush, thud + np.roll(brush, 97)])
    for t0, fq, a in ((0.22, 440.0, 0.20), (0.40, 587.33, 0.17), (0.58, 739.99, 0.14)):
        add_at(x, pan(bell(fq, 1.6, tau=0.5) * a, 0), int(t0 * SR), wrap=False)
    x = reverb(x, reverb_ir(1.8, rng, 4500), 0.25, tail=False)
    return fade(x, 0.003, 0.6)


# ---------------------------------------------------------------- 호흡 톤
def breath_tone(direction):
    """direction 'in' | 'out' | 'cycle'. 4초 들숨은 밝아지며 커지고, 4초 날숨은 어두워지며 잦아듦."""
    rng = np.random.default_rng(201)
    n = int((8 if direction == "cycle" else 4) * SR)
    t = np.arange(n) / SR
    ease = lambda u: 0.5 - 0.5 * np.cos(np.pi * np.clip(u, 0, 1))
    if direction == "in":
        e = ease(t / 3.55) * (1 - ease((t - 3.55) / 0.45))
    elif direction == "out":
        e = ease(t / 0.35) * (1 - ease((t - 0.35) / 3.65))
    else:
        e = np.where(t < 4, ease(t / 4), 1 - ease((t - 4) / 4))
    lo = pnoise(n, 250, 900, 1.5, rng)
    hi = pnoise(n, 900, 3800, 1.2, rng)
    air = (lo * (0.5 + 0.2 * e) + hi * 0.55 * e ** 2) * e ** 1.3 * 0.20
    chord = np.zeros(n)
    for f, a, br in ((220.0, 1.0, 0.0), (329.63, 0.7, 0.4), (440.0, 0.45, 0.8), (554.37, 0.25, 1.2)):
        chord += a * e ** (1.2 + br) * (np.sin(TAU * f * t) + np.sin(TAU * (f * 1.003) * t + 1.0)) * 0.5
    y = air + chord * 0.16
    x = np.stack([y, np.roll(air, 157) + chord * 0.16])
    if direction == "cycle":
        x = creverb(x, reverb_ir(1.8, rng, 4000), 0.25)
        k = int(0.02 * SR)
    else:
        x = reverb(x, reverb_ir(1.8, rng, 4000), 0.25, tail=False)
        x = fade(x, 0.01, 0.05)
    return x


if __name__ == "__main__":
    for nm, fn in (("amb_light_sea", cabin_light_sea), ("amb_kelp_forest", cabin_kelp_forest),
                   ("amb_old_gate", cabin_old_gate), ("amb_dragon_palace", cabin_dragon_palace)):
        save(nm, norm(chp(fn(), 38), target_rms=-25, peak=-6))
    save("sfx_hatch_close", norm(sfx_hatch_close(), peak=-5))
    save("sfx_dive_start", norm(sfx_dive_start(), peak=-7))
    save("sfx_surface_complete", norm(sfx_surface_complete(), peak=-7))
    save("sfx_notice_one_minute", norm(chime(((0.0, 440.0, 0.5), (0.36, 659.25, 0.42)), 2.6, 0.8, 104), peak=-9))
    save("sfx_pause", norm(chime(((0.0, 659.25, 0.4), (0.16, 493.88, 0.4)), 0.9, 0.16, 105, wet=0.12), peak=-11))
    save("sfx_resume", norm(chime(((0.0, 493.88, 0.4), (0.16, 659.25, 0.4)), 0.9, 0.16, 106, wet=0.12), peak=-11))
    save("sfx_sonar_ping", norm(sfx_sonar_ping(), peak=-9))
    save("sfx_ui_tap", norm(sfx_ui("tap"), peak=-14))
    save("sfx_ui_select", norm(sfx_ui("select"), peak=-13))
    save("sfx_layer_added", norm(sfx_layer_added(), peak=-8))
    save("breath_in_4s", norm(breath_tone("in"), peak=-12))
    save("breath_out_4s", norm(breath_tone("out"), peak=-12))
    save("breath_cycle_8s_loop", norm(breath_tone("cycle"), peak=-12))
