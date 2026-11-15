"""호흡 안내 음성 2개 — voice.py와 같은 목소리·처리 (Supertonic 3, F3, 속도 0.70).

2026-11-14 추가. 11/12 결정("호흡 안내는 음성 없이 숨소리만")을 뒤집고
숨소리 위에 음성을 겹쳐 내기로 함 [사용자 요청].

build.py는 import 하는 순간 전체 팩을 다시 만들기 때문에, 필요한 함수만 아래에 옮겨 왔다.

  python source/voice_breath.py <출력 폴더>
  → vo_f_breath_in.ogg / vo_f_breath_out.ogg (OGG Vorbis 48kHz 스테레오) + 같은 이름의 .flac 원본
"""
import os
import sys
import warnings

import numpy as np
import scipy.signal as sg
import soundfile as sf
from supertonic import TTS

warnings.filterwarnings("ignore")

SR = 48000
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/snd/out"
os.makedirs(OUT, exist_ok=True)

LINES = {
    "breath_in": "천천히 들이쉬어요.",
    "breath_out": "천천히 내쉬어요.",
}
# 한 박자(들숨/날숨)가 4초라 그 안에 끝나야 한다
MAX_SEC = 3.5


# ---- build.py에서 옮긴 함수 (동일) ----
def db(x):
    return 10 ** (x / 20)


def rms(x):
    return np.sqrt(np.mean(x ** 2) + 1e-20)


def clp(x, fc, order=2):
    f = np.fft.rfftfreq(x.shape[-1], 1 / SR)
    H = 1 / np.sqrt(1 + (f / fc) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x, axis=-1) * H, x.shape[-1], axis=-1)


def reverb_ir(rt, rng, lp=4000, pre=0.012):
    n = int(SR * rt * 1.4)
    t = np.arange(n) / SR
    ir = rng.standard_normal((2, n)) * np.exp(-6.9 * t / rt)
    ir = clp(ir, lp)
    ir[:, : int(pre * SR)] *= 0
    return ir / np.sqrt((ir ** 2).sum(axis=1, keepdims=True))


def reverb(x, ir, wet, tail=True):
    y = np.stack([sg.fftconvolve(x[c], ir[c]) for c in range(2)])
    if tail:
        out = np.zeros_like(y)
        out[:, : x.shape[1]] = x
    else:
        y = y[:, : x.shape[1]]
        out = x.copy()
    return out + wet * y


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


# ---- voice.py와 같은 처리 ----
t = TTS()
rng = np.random.default_rng(5)
ir = reverb_ir(0.7, rng, 5000)
st = t.get_voice_style("F3")
for k, text in LINES.items():
    wav, _ = t.synthesize(text, st, total_steps=24, speed=0.70, lang="ko")
    x = wav[0].astype(np.float64)
    x = sg.resample_poly(x, 160, 147)  # 44.1k → 48k
    e = np.abs(x)
    thr = e.max() * 0.02  # 앞뒤 무음 정리
    idx = np.where(e > thr)[0]
    a = max(idx[0] - int(0.03 * SR), 0)
    b = min(idx[-1] + int(0.12 * SR), len(x))
    x = x[a:b]
    x = sg.sosfilt(sg.butter(2, 80, "hp", fs=SR, output="sos"), x)
    x = x * db(-21) / rms(x)
    pk = np.abs(x).max()
    if pk > db(-4):
        x *= db(-4) / pk
    x = fade(x, 0.01, 0.06)
    st2 = np.stack([x, x])
    st2 = reverb(st2, ir, 0.07)[:, : len(x) + int(0.5 * SR)]
    st2 = fade(st2, 0, 0.3)
    dur = st2.shape[1] / SR
    if dur > MAX_SEC:
        raise SystemExit(f"{k}: {dur:.2f}s > {MAX_SEC}s — 한 박자(4초) 안에 들어가지 않음")
    name = f"vo_f_{k}"
    sf.write(f"{OUT}/{name}.flac", st2.T, SR, subtype="PCM_24")
    sf.write(f"{OUT}/{name}.ogg", st2.T, SR, format="OGG", subtype="VORBIS")
    print(f"{name:18s} {dur:5.2f}s speech {len(x) / SR:4.2f}s rms {20 * np.log10(rms(x)):.1f} "
          f"pk {20 * np.log10(np.abs(st2).max()):.1f}")
