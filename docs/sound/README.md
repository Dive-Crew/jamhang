# 잠항 사운드 v2 — 배경음 · 효과음 · 숨소리 호흡 안내

2026-11-12 제작. Pleos Playground 실행 영상(11/12)과 정책 v6 기준.
v1(`claude/jamhang-sound-v1.md`)을 대체해. 바뀐 점: **호흡 안내에서 "천천히 들이쉬어요/내쉬어요" 음성을 빼고 숨소리로 교체** [결정 11/12 지예].

> **2026-11-14 변경**: 사용자 요청으로 호흡 안내 음성을 다시 넣되 **숨소리 위에 겹쳐** 재생한다 (11/12 결정을 뒤집음).
> `vo_f_breath_in`("천천히 들이쉬어요.") / `vo_f_breath_out`("천천히 내쉬어요.")을 `source/voice_breath.py`로 같은 목소리(F3, 속도 0.70)·같은 처리로 합성했다.
> 앱 구현은 저장소의 `res/raw/`에 있고, 무손실 원본은 저장소에 넣지 않았다(스크립트로 다시 만들 수 있음).

## 저작권
- **배경음 · 효과음 · 호흡 톤**: 외부 샘플 없이 코드로 직접 합성한 원본 음원. `source/build.py`를 실행하면 같은 파일이 다시 만들어져(제작 증빙).
- **숨소리**: 사람을 녹음한 것이 아니라 합성한 소리(`source/breath.py`). 음색은 퍼블릭 도메인 녹음 "Breathing man"(PDSounds, Wikimedia Commons)의 들숨·날숨 주파수 분포를 재서 맞췄고, 그 녹음의 샘플은 들어 있지 않음. 녹음 자체는 잡음이 많고 숨 한 번이 1초 안팎이라 그대로 쓰지 않았어.
- **음성(1분 전 예고 등 선택 클립만)**: Supertonic 3(Supertone 공개 TTS, 기기 내 실행)로 합성. 모델 라이선스는 OpenRAIL-M으로, 출력물에 대한 권리를 라이선서가 주장하지 않고 상업적 사용을 막지 않음. 단 **기계가 만든 음성임을 밝혀야 함** → 앱 정보/제출 문서에 "안내 음성은 AI 합성 음성입니다" 한 줄 표기.
- 공모전 규정에 생성형 AI 사용 고지 항목이 있으면 음성 부분을 적어 둘 것 **[확인 필요]**.

## 파일 (android_res_raw/ → 앱 `res/raw/`에 그대로 복사)
형식: OGG Vorbis · 48kHz · 스테레오. 무손실 원본은 `masters_flac/`.

| 화면 · 시점 | 파일 | 길이 | 재생 방식 |
|---|---|---|---|
| 03 해치 전환 시작 | `sfx_hatch_close` | 3.9초 | 1회. 본체는 약 3초(전환 길이 [임시값]과 맞춤), '건너뛰기' 시 즉시 정지 |
| 03 → 04 넘어갈 때 | `sfx_dive_start` | 5.0초 | 1회. 끝이 사라지면서 선실 배경음으로 넘어감 |
| 04 머무름 · 빛의 바다 | `amb_light_sea` | 64초 | 반복 |
| 04 머무름 · 해초 숲 | `amb_kelp_forest` | 64초 | 반복 |
| 04 머무름 · 오래된 문 | `amb_old_gate` | 64초 | 반복 |
| 04 머무름 · 용궁 | `amb_dragon_palace` | 64초 | 반복 |
| 04 호흡 안내(켠 경우) | `breath_human_64s_loop` | 64초 | 반복. 호흡 점 애니메이션과 **같은 순간에 시작**. '안내 끄기' 시 0.5초 동안 줄이며 정지 |
| 〃 들숨 · 날숨 시작마다 (11/14 추가) | `vo_f_breath_in` / `vo_f_breath_out` | 2.1 / 2.1초 | 4초마다 번갈아 1회. 숨소리 위에 겹쳐 재생. 1분 전 예고 중에는 쉼 |
| 〃 (선택) 숨소리 밑에 깔 톤 | `breath_cycle_8s_loop` | 8초 | 반복. 숨소리와 같이 시작하면 박자가 맞음. 기본은 쓰지 않음 |
| 19 종료 1분 전 예고 | `sfx_notice_one_minute` (+ `vo_*_one_minute`) | 2.6초 | 1회 |
| 16 잠시 멈춤 / 이어서 쉬기 | `sfx_pause` / `sfx_resume` | 0.9초 | 1회 |
| 18 잠망경 | `sfx_sonar_ping` | 3.2초 | 1회 |
| 07 결과 진입 | `sfx_surface_complete` | 6.5초 | 1회. 배경음은 1초 동안 줄이며 정지 |
| 07 지층 +1겹 | `sfx_layer_added` | 2.4초 | 그날 첫 적립일 때만 |
| 버튼 · 선택 | `sfx_ui_tap` / `sfx_ui_select` | 0.2 / 0.4초 | 선택. 머무름 화면에서는 쓰지 않는 것을 권장 |

**음성 클립(선택)** — `vo_f_*` 여성 톤, `vo_m_*` 남성 톤.

| 키 | 문장 |
|---|---|
| `start` | 잠항을 시작할게요. |
| `one_minute` | 일 분 뒤에 마쳐요. |
| `finish` | 잠항을 마쳤어요. |
| `pause` / `resume` | 잠시 멈췄어요. / 이어서 쉴까요? |
| `breath_in` / `breath_out` (11/14, 여성 톤만) | 천천히 들이쉬어요. / 천천히 내쉬어요. |

## 호흡 안내 구성
실행 영상에서 잰 박자: **들숨 4초 · 날숨 4초 (한 주기 8초)**.

- `breath_human_64s_loop` = 숨 8번. 매번 세기·길이·음색이 조금씩 달라서 같은 소리가 반복되는 느낌을 줄였고, 끝이 무음이라 그대로 반복 재생됨.
- 들숨은 서서히 커지다 잦아들고, 날숨은 "하—" 하고 시작해 길게 사라짐. 들숨과 날숨 사이에 0.2~0.4초 쉼.
- 한 번씩 따로 재생하려면 `breath_human_in_4s` / `breath_human_out_4s`.
- ~~안내 문구는 화면에만 표시하고 소리로는 읽지 않음.~~ → 11/14부터 화면 문구가 바뀌는 순간(4초마다) 같은 문장을 음성으로도 읽음. 숨소리는 그대로 깔림.
- 화면 박자를 바꾸면(예: 정책 13장의 "두 번 들이쉬고, 길게 내쉬어요") 숨소리도 `source/breath.py`에서 길이를 바꿔 다시 만들어야 함 **[합의 필요: 화면 문구와 정책 문구가 다름]**.

## 음량
- 배경음 평균 −25dBFS, 숨소리는 가장 클 때 배경음보다 약 5~10dB 큼, 음성 평균 −21dBFS, 효과음 최대 −5 ~ −14dBFS.
- 권장 재생 볼륨(슬라이더 100% 기준): 배경음 0.8 · 숨소리 1.0 · 음성 1.0 · 효과음 0.8. 숨소리가 묻히면 배경음을 0.6까지 낮출 것.
- 60Hz 아래는 낮춰 둠(차량 서브우퍼에서 웅웅거리지 않게). 실제 차량 스피커에서는 **[검증 필요]**.

## 구현 메모 (Android)
```kotlin
// 반복 배경음: 선실당 1개 스트림만 재생 (정차 중 CPU 25% 기준에 여유)
val bed = MediaPlayer.create(ctx, R.raw.amb_light_sea).apply {
    isLooping = true                      // OGG는 이음매 없이 반복됨
    setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
}
// 짧은 효과음 · 음성 클립
val pool = SoundPool.Builder().setMaxStreams(3).build()
val tap = pool.load(ctx, R.raw.sfx_ui_tap, 1)

// 호흡 안내: breath_human_64s_loop 를 isLooping = true 로,
// 호흡 점 애니메이션 시작과 같은 프레임에서 start()
```
- 오디오 포커스: `AUDIOFOCUS_LOSS_TRANSIENT` → 전부 일시정지하고 "잠시 멈췄어요" 화면, 재개는 사용자가 누를 때만(정책 5장). `..._CAN_DUCK` → 볼륨 0.3배.
- 음소거 버튼(`jamhang/sound-toggle`)은 배경음·숨소리·음성·효과음 전체에 적용. 1분 전 예고음만 예외로 둘지는 **[합의 필요]**.
- 음성은 미리 만든 파일이라 TTS SDK 검증(정책 12장) 없이도 넣을 수 있음. 문장을 바꾸려면 `source/voice.py`의 `LINES`만 고쳐서 다시 실행.

## 다시 만들기
```
pip install numpy scipy soundfile supertonic
python source/build.py out/     # 배경음 · 효과음 · 호흡 톤
python source/breath.py out/    # 숨소리
python source/voice.py          # 음성 (경로는 파일 상단 OUT)
python source/voice_breath.py out/  # 호흡 안내 음성 2개 (11/14 추가, build.py 없이 단독 실행)
python source/mix.py            # 실행 영상용 미리듣기 믹스
```
