<img width="201" height="373" alt="image" src="https://github.com/user-attachments/assets/e848097b-8d4b-4fb9-9cd4-3d7837e0bc33" />
<img width="193" height="431" alt="image" src="https://github.com/user-attachments/assets/a4f884c9-4444-4daf-b020-1f25c73d0a41" />
<img width="189" height="428" alt="image" src="https://github.com/user-attachments/assets/9b6bf91d-713d-42bd-895a-8389cc704cdd" />






# **보이스 피싱 탐지 프로젝트**

### **핵심기능**

- whisper모델을 튜닝하여 만든 실시간 음성 변조 탐지 기능
- pynote모델과 whisper STT 모델을 이용한 화자분리와 대화내용 출력
- 음성 스트림의 전사 텍스트에 정규식 위험 패턴을 적용한 위험 점수 산출 (공개 `/v1/stream` 구현)
- Qwen LLM 모델을 이용한 보이스 피싱 상담 챗봇 기능
- 어플 연동을 통한 실제 통화어플 처럼 사용 가능

### **데이터 처리 구조와 구현 범위**

- 음성 바이트 → 모노 변환·16 kHz 리샘플링 → Whisper 입력 피처·3000 프레임 패딩/절단 → encoder + MLP → 변조 확률
- pyannote 화자 분리 → 구간별 Whisper STT → WebSocket 전사 결과 전달
- 별도 상담 질문 → 참고 문서 검색 → Qwen 등 설정된 OpenAI 호환 모델 → 답변과 출처
- 학습 manifest에 경로·라벨·출처·화자·길이·split 정보를 관리합니다.
- Qwen 문맥 판별과 음성 스트림의 통합, 실제 세션 보고서, 환경별 지연시간 검증은 확장 과제입니다. 공개 보고서 API는 고정 예시를 반환합니다.
- 학습 노트북의 평가 지표는 EER이며 정확도 98%의 근거로 사용하지 않습니다.
- 2026-10-03 보완: RAG 근거·대화 이력 전달, PCM16 입력의 WAV 변환, ASR 작업 스레드 실행, WebSocket 연결 종료 처리.
- 오프라인 회귀 테스트: 저장소 루트에서 `python -m unittest discover -s tests -v`.
- 자세한 구현 범위와 검증 한계는 [PORTFOLIO_VALIDATION.md](PORTFOLIO_VALIDATION.md)를 참고하세요. Colab에서 사용하는 기존 ZIP은 수정 소스로 다시 패키징해야 합니다.

### **사용 모델 및 데이터셋(상세 내용은 License.md확인)**

- 사용모델
  - whisper base모델(encoder부분을 튜닝하여 변조탐지 모델로 학습)
  - whisper STT모델(통화 내용 분석용 STT에 사용)
  - pyannote.audio모델(화자분리와 화자 임베딩용으로 사용)
  - Qwen모델(통화 내용 분석과 챗봇을 위한 LLM모델로 사용)
- 데이터셋(본 프로젝트의 Whisper 변조 탐지 모델은 아래의 데이터셋을 이용하여 학습되었습니다.)
  - KSS(원본음성 데이터셋)
  - ASVspoof 2019(원본음성+변조음성 데이터셋)
  - LibriSpeech(원본음성 데이터셋)
  - VCTK(원본음성 데이터셋)
  - WaveFake(변조음성 데이터셋)

### **프로젝트 개요 파일**

- Team Aura\_발표자료.pdf
  - 프로젝트 소개와 프로젝트에 대한 발표 내용
- Aura.pdf
  - 프로젝트의 피그마 시트
- Aura_testcase.xlsx
  - 프로젝트의 테스트 시트
- License.md
  - 프로젝트에 사용한 모델과 모델학습에 사용한 데이터셋의 라이센스 정의

### **전체 프로젝트 구조(각 세부내용은 각 폴더내의 README.md 확인)**

- 프론트엔드(voiceFront 폴더)
  - React(타입스크립트)와 Next.js로 구성
- 웹 백엔드(voiceBack 폴더)
  - SpringBoot로 구성
- AI 백엔드(ModelBackEnd 폴더)
  - 파이썬 FastAPI로 구성
- APP(auraAPP 폴더)
  - 네이티브 웹뷰 어플로 구성

![alt text](aura.png)
