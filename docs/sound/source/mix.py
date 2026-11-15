import numpy as np, soundfile as sf, sys
sys.path.insert(0,'/tmp/snd')
from build import SR, fade, db
O='/tmp/snd/out'
def ld(n):
    x,sr=sf.read(f"{O}/{n}.wav"); assert sr==SR; return x.T
def put(buf,x,t,g=1.0):
    s=int(round(t*SR)); m=min(x.shape[1],buf.shape[1]-s)
    if m>0: buf[:,s:s+m]+=x[:,:m]*g
# ---- 화면 녹화에 맞춘 미리듣기 믹스
D=133.44; n=int(D*SR); mix=np.zeros((2,n)); t=np.arange(n)/SR
def env(points):
    ts,vs=zip(*points); return np.interp(t,ts,vs)
def bed(name,points,offset=0.0):
    a=ld(name); reps=int(np.ceil((n/SR+offset)/64))+1
    a=np.tile(a,reps)[:, int(offset*SR): int(offset*SR)+n]
    return a*env(points)
# 세션 1: 빛의 바다 (7.9~20.0, 18.3 종료 확인 팝업에서 낮춤)
mix+=bed('amb_light_sea',[(0,0),(7.6,0),(10.0,1),(18.3,1),(18.8,0.35),(19.9,0.35),(21.2,0),(D,0)])
# 세션 2: 해초 숲 + 호흡 안내 (34.3~99.0, 97.5 팝업)
mix+=bed('amb_kelp_forest',[(0,0),(34.0,0),(36.5,0.85),(97.5,0.85),(98.0,0.3),(99.0,0.3),(100.5,0),(D,0)])
g=ld('breath_human_64s_loop'); g=g[:, :int((97.6-34.4)*SR)]; g=fade(g,0,0.6); put(mix,g,34.4,db(4))
for tt in (4.2,21.4,23.6,30.7,97.4,98.9,116.0,125.0): put(mix,ld('sfx_ui_tap'),tt)
for tt in (26.9,28.9,30.0): put(mix,ld('sfx_ui_select'),tt)
put(mix,ld('sfx_hatch_close'),4.35); put(mix,ld('sfx_dive_start'),7.3)
put(mix,ld('sfx_surface_complete'),19.6)
put(mix,ld('sfx_hatch_close'),30.85); put(mix,ld('sfx_dive_start'),33.7)
put(mix,ld('sfx_surface_complete'),98.7)
put(mix,ld('sfx_layer_added'),104.6)
mix*=db(3)
print('demo peak',round(20*np.log10(abs(mix).max()),1))
sf.write('/tmp/snd/demo_mix.wav',mix.T,SR,subtype='FLOAT')
