import numpy as np, soundfile as sf, scipy.signal as sg, sys, warnings
warnings.filterwarnings("ignore")
sys.path.insert(0,'/tmp/snd')
from build import SR, clp, chp, rms, db, reverb, reverb_ir, fade
from supertonic import TTS
OUT='/tmp/snd/out'
LINES={
 'start':'잠항을 시작할게요.',
 'one_minute':'일 분 뒤에 마쳐요.',
 'finish':'잠항을 마쳤어요.',
 'pause':'잠시 멈췄어요.',
 'resume':'이어서 쉴까요?',
}
t=TTS()
rng=np.random.default_rng(5)
ir=reverb_ir(0.7,rng,5000)
for tag,voice,spd in (('f','F3',0.70),('m','M2',0.70)):
    st=t.get_voice_style(voice)
    for k,text in LINES.items():
        wav,_=t.synthesize(text, st, total_steps=24, speed=spd, lang='ko')
        x=wav[0].astype(np.float64)
        x=sg.resample_poly(x,160,147)          # 44.1k → 48k
        e=np.abs(x); thr=e.max()*0.02           # 앞뒤 무음 정리
        idx=np.where(e>thr)[0]; a=max(idx[0]-int(0.03*SR),0); b=min(idx[-1]+int(0.12*SR),len(x))
        x=x[a:b]
        x=sg.sosfilt(sg.butter(2,80,'hp',fs=SR,output='sos'),x)
        x=x*db(-21)/rms(x)
        pk=np.abs(x).max()
        if pk>db(-4): x*=db(-4)/pk
        x=fade(x,0.01,0.06)
        st2=np.stack([x,x])
        st2=reverb(st2,ir,0.07)[:, :len(x)+int(0.5*SR)]
        st2=fade(st2,0,0.3)
        sf.write(f"{OUT}/vo_{tag}_{k}.wav", st2.T, SR, subtype='PCM_24')
        print(f"vo_{tag}_{k:14s} {st2.shape[1]/SR:5.2f}s speech {len(x)/SR:4.2f}s rms {20*np.log10(rms(x)):.1f} pk {20*np.log10(np.abs(st2).max()):.1f}")
