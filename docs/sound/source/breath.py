"""사람 숨소리 합성 — 들숨 4초 · 날숨 4초.
음색(공명 위치)은 퍼블릭 도메인 녹음 'Breathing man'(PDSounds, Wikimedia Commons)의
들숨·날숨 스펙트럼을 재서 맞췄고, 소리 자체는 전부 새로 합성했음(녹음 샘플은 들어 있지 않음)."""
import numpy as np, soundfile as sf, sys
sys.path.insert(0, '/tmp/snd')
from build import SR, TAU, db, rms, reverb, reverb_ir, fade

OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/snd/out'


def shaped_noise(n, formants, tilt, lo, hi, rng):
    """formants: [(Hz, 대역폭Hz, dB)] 공명을 가진 숨 노이즈"""
    f = np.fft.rfftfreq(n, 1 / SR)
    mag = np.zeros_like(f)
    for fc, bw, g in formants:
        mag += db(g) / (1 + ((f - fc) / (bw / 2)) ** 2)
    mag += db(tilt[0]) * (np.maximum(f, 50) / 1000.0) ** (-tilt[1])      # 바탕 공기 소리
    mag *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1)) ** 6) / np.sqrt(1 + (f / hi) ** 6)
    x = np.fft.irfft(mag * np.exp(1j * rng.uniform(0, TAU, len(f))), n)
    return x / rms(x)


def ease(u):
    return 0.5 - 0.5 * np.cos(np.pi * np.clip(u, 0, 1))


def flutter(n, rng, depth):
    """기류의 잔떨림(6~25Hz 불규칙 변동)"""
    f = np.fft.rfftfreq(n, 1 / SR)
    m = ((f > 3) & (f < 25)).astype(float)
    y = np.fft.irfft(m * np.exp(1j * rng.uniform(0, TAU, len(f))), n)
    return 1 + depth * y / (np.abs(y).max() + 1e-12)


def inhale(rng, dur=4.0, v=None):
    v = v or {}
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = v.get('f', 1.0)
    a = shaped_noise(n, [(540 * k, 300, 0), (1350 * k, 600, -12), (2500 * k, 900, -19)], (-26, 0.5), 230, 4200, rng)
    b = shaped_noise(n, [(600 * k, 300, 0), (1500 * k, 600, -10), (2700 * k, 900, -16)], (-24, 0.4), 250, 4800, rng)
    m = ease(t / (dur * 0.9))                      # 들이쉴수록 공명이 살짝 올라감
    t0, t1 = v.get('on', 0.12), v.get('off', 3.72)
    env = ease((t - t0) / 0.55) * (0.45 + 0.55 * ease((t - t0) / 2.6)) * (1 - ease((t - (t1 - 0.75)) / 0.75))
    return (a * (1 - m) + b * m) * env * flutter(n, rng, 0.10)


def exhale(rng, dur=4.0, v=None):
    v = v or {}
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = v.get('f', 1.0)
    a = shaped_noise(n, [(520 * k, 340, 0), (1300 * k, 600, -13), (2900 * k, 900, -15), (180, 150, -4)], (-28, 0.6), 110, 4200, rng)
    b = shaped_noise(n, [(450 * k, 320, 0), (1150 * k, 560, -16), (2600 * k, 900, -19), (170, 150, -3)], (-30, 0.7), 100, 3400, rng)
    m = ease(t / (dur * 0.85))                     # 내쉴수록 낮고 부드럽게
    t0, t1 = v.get('on', 0.10), v.get('off', 3.80)
    env = ease((t - t0) / 0.35) * (1 - 0.96 * ease((t - t0 - 0.3) / (t1 - t0 - 0.3)) ** 0.8) * (1 - ease((t - (t1 - 0.3)) / 0.3))
    return (a * (1 - m) + b * m) * env * flutter(n, rng, 0.07) * 1.15


def stereo(x, rng, width=0.18):
    side = np.roll(x, int(0.0007 * SR)) * width
    return np.stack([x + side, x - side])


def cycle(rng, vary=True):
    v_in = {'f': rng.uniform(0.96, 1.04), 'on': rng.uniform(0.08, 0.2), 'off': rng.uniform(3.6, 3.8)} if vary else None
    v_out = {'f': rng.uniform(0.96, 1.04), 'on': rng.uniform(0.06, 0.16), 'off': rng.uniform(3.7, 3.9)} if vary else None
    g = db(rng.uniform(-1.2, 1.2)) if vary else 1.0
    return np.concatenate([inhale(rng, v=v_in), exhale(rng, v=v_out)]) * g


def finish(x, rng, peak):
    st = stereo(x, rng)
    st = reverb(st, reverb_ir(0.45, rng, 4500, pre=0.004), 0.10, tail=False)
    return st * db(peak) / np.abs(st).max()


def save(name, x):
    sf.write(f"{OUT}/{name}.wav", x.T, SR, subtype='PCM_24')
    print(f"{name:26s} {x.shape[1]/SR:6.2f}s rms {20*np.log10(rms(x)):6.1f} peak {20*np.log10(np.abs(x).max()):6.1f}")


if __name__ == '__main__':
    rng = np.random.default_rng(321)
    one_in, one_out = inhale(rng), exhale(rng)
    ref = np.abs(np.concatenate([one_in, one_out])).max()
    save('breath_human_in_4s', fade(finish(one_in, rng, -9 + 20 * np.log10(np.abs(one_in).max() / ref)), 0.005, 0.02))
    save('breath_human_out_4s', fade(finish(one_out, rng, -9), 0.005, 0.02))
    # 64초 = 8번의 호흡, 매번 조금씩 다르게(기계적으로 들리지 않게). 끝이 무음이라 그대로 반복 가능
    long = np.concatenate([cycle(rng) for _ in range(8)])
    save('breath_human_64s_loop', fade(finish(long, rng, -9), 0.005, 0.02))
