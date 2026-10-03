# 포트폴리오와 구현 확인 (2026-10-03)

## 확인한 데이터 흐름

- 변조 탐지: 음성 바이트 → 모노 변환 → 필요 시 16 kHz 리샘플링 → Whisper feature extractor → 3000 프레임 패딩/절단 → Whisper encoder + MLP → spoof_prob.
- 화자별 전사: pyannote 화자 분리 → 구간별 Whisper medium STT → WebSocket stt_segment 응답.
- 상담: 참고 문서 검색 → OpenAI 호환 API로 Qwen 등 설정된 모델 호출 → 답변과 출처 반환.
- voiceguard_backend_chat_patch의 /v1/stream 위험 점수는 정규식 risk_score 기반이며 Qwen 추론 결과가 아니다.
- /v1/sessions/{session_id}/report는 고정 예시 응답이다.

## 이번 수정

- 검색된 참고 문서와 이전 user/assistant 대화를 실제 LLM 요청에 전달한다. 사용자 입력의 system 역할은 제외한다.
- PRIMARY_MODEL/SECONDARY_MODEL이 없는 경우 CHAT_MODEL 설정을 존중한다.
- 사용하지 않는 llama_cpp import를 제거해 해당 패키지가 없어도 HTTP 기반 챗 코드를 불러올 수 있게 한다.
- PCM16 스트림을 16 kHz 모노 WAV로 감싸 ASR에 전달한다. 홀수 바이트의 마지막 샘플 조각은 다음 입력까지 보관한다.
- ASR 호출과 전사 결과 순회를 작업 스레드에서 수행한다.
- 두 WebSocket 핸들러에서 disconnect 메시지를 받으면 반복을 종료한다.

## 검증

저장소 루트에서 `python -m unittest discover -s tests -v`로 6개 오프라인 회귀 테스트를 실행한다.
실제 코드의 함수/클래스를 AST로 로드하고, 외부 HTTP/모델 호출만 대체하여 입력 변환과 요청 내용을 검증한다.
전체 서비스 import, GPU 추론, 실제 통화, DB 연결, 배포 또는 지연시간 측정까지 검증한 것은 아니다.
Colab 노트북에서 업로드해 사용하는 ZIP은 이번 로컬 소스 수정과 자동 동기화되지 않는다. 배포 전 수정 소스로 다시 패키징해야 한다.

## 추가 확인이 필요한 노션 표현

- '98% 정확도': Whisper.ipynb에 기록된 EER와 다른 지표다. 평가 데이터, 지표 정의, 재현 가능한 로그가 필요하다.
- 'UniSpeech 구현': 현재 공개 코드에서 확인하지 못했다.
- '서버 성능 최적화로 실시간 응답 개선': 수정 전후 동일 조건의 지연시간 측정이 필요하다.
- 팀원 수, 개인 담당 역할, 실제 발표 당시 결과는 코드만으로 검증할 수 없다.

이번 수정은 2026-10-03 이후의 보완 작업이다. 과거 프로젝트 당시의 개인 성과로 소급하여 기재하지 않는다.
