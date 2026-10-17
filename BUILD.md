# 우리톡 Android 1.8.1

- package: `kr.wooritalk.app`
- minSdk: Android 9 (API 28)
- Native WebView + Firebase Cloud Messaging
- 사진/영상 다운로드: Download 폴더 저장 후 실제 Download 파일 경로를 MediaScanner에 등록 → 갤러리 노출
- Chrome 앱이 없어도 동작하며 Android System WebView는 필요합니다.

Android Studio에서 프로젝트를 열고 `Build > Generate Signed App Bundle / APK > APK`로 release APK를 빌드하세요.
Firebase 설정은 앱이 NAS `/api/mobile/config`에서 읽으므로 google-services Gradle 플러그인은 사용하지 않습니다.
