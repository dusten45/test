# Paladog 2.2.0 오프라인·최신 Android 호환 수정본

이 브랜치는 사용자 제공 APK에 적용한 개인정보 보호·오프라인 패치와 빌드·검증 자료를 보관합니다. `main`은 작업 전 커밋의 `README.md`만 유지합니다.

## Linux 독립 실행판

`paladog/linux-standalone` 브랜치에는 [Linux 실행판과 빌드 방법](linux/README.md)을 추가했습니다. 기존 게임 바이트코드를 JVM으로 옮기고 Linux OpenGL·입력·오디오·저장 계층을 연결합니다. Android OS나 에뮬레이터를 사용하지 않으며, 이번 범위는 Linux x86-64입니다.

Java를 동봉한 실행 묶음과 대응 소스 묶음은 `linux/package.sh`로 만듭니다. 생성 파일·도구 캐시·시험 세이브는 Git에 넣지 않습니다.

## 결과물

- [최종 APK](paladog-work/output/Paladog-2.2.0-modern-offline.apk)
- [수정·검증 보고서](paladog-work/output/Paladog-modern-build-notes.md)
- [빌드 재현 절차](paladog-work/output/REPRODUCE.md)
- [APK SHA-256](paladog-work/output/Paladog-2.2.0-modern-offline.apk.sha256)

최종 APK의 SHA-256은 `c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`이며, 크기는 40,227,841바이트입니다. 패키지는 `com.Paladog.KorGG`, versionCode는 26입니다.

마케팅 동의·홍보 UI, 개인정보 조회와 외부 통신을 제거·차단했습니다. 미사용 SDK의 ARM32 라이브러리를 제거하고, 최신 Android 폰트에서 생기는 안내문 잘림을 수정했습니다. 게임 콘텐츠·해금 조건·로컬 저장 구현은 보존했습니다.

Android 6에서 최종본 업데이트·기존 전투 복원·안내문 표시를 확인했고, Android 16의 64비트 전용 x86_64 에뮬레이터에서는 최종본으로 정상 저장·종료·재실행·실제 전투 복원을 확인했습니다. 이 개발 환경에서는 Galaxy S26·ARM64·One UI 실기기를 직접 시험하지 않았습니다. 이전 후보의 전체 실행 시험과 최종 글자 수정본의 회귀 시험은 보고서에서 구분합니다.

2026-10-18 사용자는 Galaxy S26·One UI 9.0에서 약 5분간 기본 기능·소리·그래픽이 정상 작동했다고 보고했습니다. 이는 별도의 사용자 실기기 관측이며, 게임의 모든 기능을 전수 검증한 결과는 아닙니다.

## 저장소 구성

- `paladog-work/`: 패치·재빌드·검증 스크립트, 결과 APK, 보고서와 선택된 실행 화면.
- `paladog-tools/`: 고정 버전 도구 설치 스크립트와 격리 Android 검증 환경의 시작·통신 차단 절차.

원본 APK·분할 첨부, 개인 서명키·비밀번호, 전체 디컴파일 결과, 도구 바이너리·에뮬레이터 디스크, 원시 로그와 테스트 세이브는 포함하지 않습니다. 보고서에서 참조하는 원시 로그·세이브 중 일부는 게시 대상에서 제외됐습니다.

## 재현할 때

스크립트는 `/workspace/paladog-work`와 `/workspace/paladog-tools`를 절대 경로로 사용합니다. 이 저장소를 `/workspace/test`에 clone했다고 해서 빌드가 저장소 안의 파일을 자동으로 사용하는 것은 아닙니다.

재현은 별도의 빈 환경에서 [REPRODUCE.md](paladog-work/output/REPRODUCE.md)에 따라 진행하세요. 위 두 디렉터리를 해당 경로에 배치하고, 문서에 지정된 해시의 원본 APK를 직접 준비해야 합니다. Git에 포함된 배포 APK와 검증 기록은 재빌드 출력과 구분해 보관하세요. 기존 클라우드 작업 폴더나 `private/`를 덮어쓰지 마세요. 이 브랜치 게시 과정에서는 APK를 재빌드하지 않았습니다.

원본 앱과 서명이 다르므로 기존 앱에 진행 데이터가 있다면 백업 확인 없이 삭제하지 마세요. 개인키는 게시하지 않으며, 새 키로 재빌드한 파일은 전달 APK와 인증서·해시가 달라집니다.
