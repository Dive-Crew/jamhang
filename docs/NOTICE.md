# 잠항 — 표기 사항 (제출 자료용)

## AI 합성 음성
**안내 음성은 AI 합성 음성입니다.**

- 대상: 1분 전 예고 음성, 호흡 안내 음성("숨을 들이쉬세요" / "천천히 내쉬세요")
- 합성: Supertonic 3 (Supertone 공개 TTS, 기기 밖에서 미리 합성해 앱에 녹음 파일로 넣음)
- 라이선스: OpenRAIL-M — 출력물 권리 주장 없음, 상업적 사용 허용, **기계가 만든 음성임을 밝혀야 함**
- 앱 안 표기: 프로필과 기록 시트 맨 아래 "안내 음성은 AI 합성 음성입니다 (호흡 안내 포함 · Supertonic 3)"
- 만든 방법: `docs/sound/source/voice.py`, `docs/sound/source/voice_breath.py`

## 음성 입력
한 줄 메모 음성 입력은 Pleos SpeechToText SDK를 쓴다. 음성은 앱에 저장하지 않고, 글자로 바뀐 결과만 사용자가 [남기기]를 눌렀을 때 저장한다.
