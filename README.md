# 페이지 본문 (PageText)
삼성 인터넷 → 공유 → "페이지 본문" 을 선택하면, 페이지를 WebView로 열어 본문 전체를 한 번에 추출해서
일반 텍스트 화면으로 보여줍니다. 롱프레스 "모두 선택" / "전체복사"가 항상 같은 범위(추출된 본문 전체)로 동작합니다.

## 빌드 (GitHub Actions)
1. 이 폴더를 GitHub 저장소에 push (main 브랜치)
2. Actions 탭 → "Build APK" 실행 → Artifacts 의 PageText-debug-apk 다운로드
3. app-debug.apk 설치 (알 수 없는 앱 설치 허용 필요)

## 진단
- 크래시 시 팝업(원인 체인 + 최근 로그), 화면 하단 "로그" 버튼으로 단계별 로그 확인
- 로그 파일: Android/data/com.soby.pagetext/files/debug.log
