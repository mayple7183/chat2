# 우리톡 Android 1.8.2 FCM 수정

- Firebase Android 표준 초기화 방식으로 전환 (`google-services.json` + Google Services Gradle plugin)
- 패키지: `kr.wooritalk.app`
- 기존 NAS `/api/fcm/register` 등록 흐름 유지
- FCM 토큰 발급 실패 원인을 앱 내부에 보관하도록 진단 추가
- 1.8.1의 HttpsURLConnection import 수정 포함

중요: `woori-talk-firebase-adminsdk-....json` 같은 서비스 계정 비밀키는 GitHub에 업로드하지 마세요. 서버(NAS)에만 보관해야 합니다.
