# 원본 APK에서 개인정보 보호 수정본 재현하기

이 문서는 기본 개인정보 보호 수정본과 최신 Android용 수정본의 빌드 절차를 재현하는 방법이다. 최신 기기용 수정본도 먼저 기본 패치를 만든 뒤 파생한다. 최종 APK의 게임 플레이·저장·통신 차단에 대한 실행 검증은 `Paladog-modern-build-notes.md`와 그 문서가 참조하는 최종 실행 보고서를 확인한다. `Paladog-offline-build-notes.md`는 기본 패치의 배경과 변경 범위를 설명한다. 정적 검사 통과만으로 게임 전체 동작이나 악성코드 부재가 입증되지는 않는다.

## 필요한 파일과 환경

- Linux x86_64, Java/JDK 21의 `java`·`keytool`, Python 3.9 이상, Bash, `curl`, `unzip`, `sha256sum`.
- 도구 버전: Apktool 2.11.1, JADX 1.5.1, Android SDK Build-Tools 35.0.0.
- 사용자가 보유한 원본 APK: 41,046,844바이트, SHA-256 `db9d5c399d7f436a27dba28eebd66771fdc29c812c0e06008c67409bd595a6e1`.
- 빌드 스크립트 다섯 개: `apply_privacy_patch.py`, `assemble_minimal_apk.py`, `verify_static.py`, `rebuild.sh`, `sign-build.sh`.
- 최신 Android용 추가 스크립트 네 개: `prepare_modern.py`, `assemble_modern_apk.py`, `rebuild-modern.sh`, `verify_modern.py`.
- 도구 설치 스크립트: `/workspace/paladog-tools/install.sh`.

스크립트는 `/workspace/paladog-work`와 `/workspace/paladog-tools`를 절대 경로로 사용한다. 아래 절차는 그 경로에 이전 `decoded`, `patched`, `verified-decode`, `modern-patched`, `modern-verified-decode`가 없는 새 작업 환경을 전제로 한다. 현재 작업 폴더의 기존 산출물이나 개인키를 삭제하지 말고, 재현 시험은 별도 환경에서 수행한다. 경로를 바꾸려면 문서의 명령뿐 아니라 각 Python/Bash 스크립트의 고정 경로도 함께 수정해야 한다.

원본 APK, 완전한 디컴파일 결과, Android/JADX 도구 바이너리, 개인키·비밀번호는 재현 자료 ZIP에 포함하지 않는다. 해당 원본 해시와 다른 APK에는 이 패치를 적용하지 않는다.

## 1. 도구 설치와 입력 준비

재현 자료를 `/workspace` 아래에 풀어 위 경로 배치를 맞춘 다음, 원본 APK를 `/workspace/paladog-work/input/original.apk`에 직접 준비한다. APK를 실행하거나 설치하는 단계는 아니다.

```bash
set -euo pipefail
mkdir -p /workspace/paladog-work/input /workspace/paladog-work/output /workspace/paladog-work/evidence
bash /workspace/paladog-tools/install.sh
cd /workspace/paladog-work
python3 - <<'PY'
from pathlib import Path
import hashlib
p = Path('input/original.apk')
assert p.stat().st_size == 41046844
assert hashlib.sha256(p.read_bytes()).hexdigest() == 'db9d5c399d7f436a27dba28eebd66771fdc29c812c0e06008c67409bd595a6e1'
for name in ('decoded', 'patched', 'verified-decode', 'modern-patched', 'modern-verified-decode'):
    assert not Path(name).exists(), '기존 작업 보존 필요: ' + name
print('원본 크기·해시 및 새 작업 경로 확인 완료')
PY
/workspace/paladog-tools/android/build-tools/35.0.0/apksigner verify --verbose --print-certs input/original.apk > evidence/original-signature.txt
```

설치 스크립트는 공식 HTTPS 배포처에서 고정 버전을 내려받고 체크섬을 검증한다. 네트워크 정책과 TLS 인증서 검증을 유지해야 하며, 내려받기에 실패하면 검증을 끄지 말고 실제 접근 문제를 해결한다. 합성 APK 도구 시험도 수행하지만 팔라독 실행 시험을 대신하지 않는다.

Android 실행 검증 환경까지 준비하려는 경우에만 `bash /workspace/paladog-tools/install-with-runtime.sh`를 추가로 사용한다. 이 선택적 스크립트는 같은 빌드 도구와 함께 공식 API23 ARM32 및 API36 x86_64 에뮬레이터·대용량 시스템 이미지를 내려받아 검증한다. 다운로드·압축 해제·게스트 디스크를 위한 충분한 여유 공간이 필요하다. APK 빌드에는 기본 `install.sh`만으로 충분하다. 선택적 설치도 에뮬레이터나 APK를 실행하지 않으며, 실제 시작·기기 준비·매 부팅 후 방화벽 적용은 `start-instructions.md`를 따른다. 포함된 자료에는 시스템 이미지, 게스트 디스크, 원시 상태 기록과 로그가 없다.

이번 클라우드에는 KVM이 없어 소프트웨어 에뮬레이션의 부팅·화면 갱신·시험이 느렸다. `prepare-modern-runtime.sh`가 정상적인 userdebug 권한으로 적용하는 timeout multiplier 10은 느린 시험용 Android 게스트의 시스템 시작 대기시간을 늘리는 설정이며, SELinux 정책은 유지한다. APK 수정 사항이나 실제 휴대전화의 실행 요건이 아니므로 개인 휴대전화에 이 설정을 적용할 필요는 없다.

## 2. 원본 분해 후 패치 적용

```bash
set -euo pipefail
cd /workspace/paladog-work
/workspace/paladog-tools/bin/apktool d -p /workspace/paladog-tools/framework input/original.apk -o decoded > evidence/decode.log 2>&1
python3 apply_privacy_patch.py
```

`apply_privacy_patch.py`는 원본 SHA-256을 다시 확인한 뒤 `decoded`를 `patched`로 복사한다. 원본 APK와 `decoded`는 변경하지 않는다. `patched`가 이미 있으면 중단하므로 기존 작업을 지우고 무심코 반복 실행하지 않는다. 패치는 Manifest와 Smali 클래스 네 개만 변경하고, `evidence/changed-files.json`과 `evidence/privacy.patch`를 기록한다.

## 3. 기본 개인정보 보호 수정본 빌드·정렬·서명

```bash
set -euo pipefail
cd /workspace/paladog-work
bash rebuild.sh > evidence/build.log 2>&1
```

이 명령은 순서대로 다음을 수행한다.

1. Apktool로 `patched`를 `output/paladog-apktool-unsigned.apk`로 빌드한다.
2. `assemble_minimal_apk.py`가 재빌드 결과에서 `AndroidManifest.xml`과 `classes.dex`만 취한다. 원본 APK의 나머지 항목은 그대로 가져오고 기존 서명 항목 세 개를 제거해 `output/paladog-offline-unsigned.apk`를 만든다.
3. `sign-build.sh`가 ZIP 정렬, 개인키 서명, APK 서명 검증, 정렬 검사를 수행한다. 최종 파일은 `output/Paladog-2.2.0-offline-privacy.apk`이며 서명 결과는 `evidence/patched-signature.txt`다.

첫 서명 시 `private/offline-signing.p12`와 `private/keystore-password`를 로컬에 생성한다. 기존 키 파일이 있으면 재사용한다. 키와 비밀번호 중 하나만 유실된 경우 새 파일을 임의로 섞지 말고 안전하게 보관한 동일 쌍을 복원해야 한다. 이 두 파일은 공개 묶음에 포함하지 않는다.

**새로 생성한 키로 만든 APK의 인증서와 SHA-256은 이번에 전달한 APK와 다르다.** 이는 예상된 결과다. 이 절차는 수정 내용과 검사를 재현하며 바이트 단위로 동일한 서명 APK를 보장하지 않는다. 전달된 APK와 같은 서명으로 후속 업데이트를 만들려면 최초 빌드의 개인키·비밀번호를 안전하게 보존해야 한다. 새 키로 서명한 APK는 전달된 APK나 원본 앱 위에 일반 업데이트로 설치할 수 없다. 기존 진행 데이터가 있다면 삭제 전에 검증된 백업과 이전 절차가 필요하다.

## 4. 기본 수정본을 다시 분해해 검사

`rebuild.sh`만으로 아래 정적 검사 전체가 실행되지는 않는다. 반드시 최종 서명 APK에서 새로 분해한 결과를 사용한다.

```bash
set -euo pipefail
cd /workspace/paladog-work
test ! -e verified-decode
/workspace/paladog-tools/bin/apktool d -p /workspace/paladog-tools/framework output/Paladog-2.2.0-offline-privacy.apk -o verified-decode > evidence/final-decode.log 2>&1
python3 verify_static.py
sha256sum output/Paladog-2.2.0-offline-privacy.apk > output/Paladog-2.2.0-offline-privacy.apk.sha256
```

성공하면 `evidence/static-validation.json`에 이 작업에서 정의한 검사 34개의 통과 결과와 실제 출력 SHA-256이 기록된다. 핵심 기대값은 Manifest/DEX 두 항목만 변경, 나머지 4,767개 항목 동일, 권한은 `VIBRATE`만 존재, 로컬 저장·암호화 클래스와 기존 라이선스 정책 파일 보존이다. `evidence/patched-signature.txt`에서 v1/v2/v3 서명 검증 결과도 확인한다. 검사 스크립트 자체의 서명 문자열 확인은 v1/v2를 대상으로 한다.

최종 기본 수정본에는 서버 공지용 `What's New` 고양이 버튼(UI ID 47)을 숨기는 변경도 들어 있다. 이에 따라 기본 APK의 기록된 SHA-256은 `92f93b8bf639f1765457d8dd3b83bc440089d4d885c2d244e94445fbc0533213`이다. 앞선 임시 빌드의 `4f95219cc9ed6b33aa3680be24ca463717df190ed2a3ae1b926cb6c2be1226bc`는 최신 기본 빌드 해시가 아니다. 새 개인키로 재빌드한 결과의 해시는 다시 달라진다.

Java 형태의 추가 검토가 필요하면 다음을 별도로 실행한다. 실제 패치는 JADX가 복원한 Java를 컴파일하는 방식이 아니다.

```bash
set -euo pipefail
cd /workspace/paladog-work
test ! -e final-jadx
/workspace/paladog-tools/bin/jadx --no-res -d final-jadx output/Paladog-2.2.0-offline-privacy.apk > evidence/final-jadx.log 2>&1
```

검사 결과는 패치가 검사한 조건을 충족한다는 뜻이다. 남아 있는 모든 SDK·JNI·콜백의 안전성이나 모든 기기 호환성을 증명하지는 않는다. 새 빌드의 실제 실행·캠페인·저장 복원은 개인 데이터가 없는 격리 Android 환경에서 별도로 재검증한다.

## 5. 최신 Android용 수정본 준비·빌드·검증

위 1–4단계가 끝나고 기본 수정본의 `patched`, 원본 `decoded`, 서명키, `evidence/patched-signature.txt`가 있는 상태에서 진행한다. 최신 Android용 수정본은 정적 참조 검사에서 코어의 의존성이 없는 SDK 클래스 928개와 ARM32 라이브러리 네 개를 제거한다. 남은 클래스 1,176개 중 1,175개는 기본 개인정보 보호 패치 그대로 보존하며, `CCTexture2D.WrapText`에는 글꼴 호환성 수정 한 곳을 적용한다. 전체 문자열의 `Paint.measureText` 너비가 허용 폭 이내면 한 줄로 유지해, 자동 크기의 한 줄 텍스처가 최신 글꼴에서 잘못 다시 줄바꿈되는 문제를 막는다. 화면 비율, 게임 진행·저장 로직을 바꾸는 수정은 아니다.

이는 ARM32 바이너리를 ARM64로 변환하는 방식이 아니다. 게임이 네이티브 SDK에 의존하지 않는다는 분석을 바탕으로 해당 SDK를 제거해 Android 런타임이 DEX 코드를 처리하는 구성이다. 앱의 `versionCode`는 26, `versionName`은 `2.2.0-offline`, target SDK는 28로 설정한다. min SDK 7, 가로 화면, 로컬 저장 구조와 해금 조건은 유지한다. 화면 크기 변경 처리와 선택적 `org.apache.http.legacy` 선언도 추가한다. target SDK를 최신 숫자로 바꾸는 것만으로 호환성을 입증할 수 없으며, 갤럭시 S26 실기기 검증 여부는 실행 보고서와 구분한다.

재현 ZIP에는 이전 빌드의 `modern-prepare.json`도 증거로 포함된다. 준비 스크립트가 이를 덮어쓰지 않도록 실행 전에 해당 참고본을 별도 위치로 옮긴다. 새로 생성한 `modern-patched`나 준비 증거를 삭제해 반복 실행하는 명령은 아래에 포함하지 않는다.

```bash
set -euo pipefail
cd /workspace/paladog-work
test ! -e modern-patched
test ! -e modern-verified-decode
if [ -f evidence/modern-prepare.json ]; then
    mkdir -p reference-evidence
    test ! -e reference-evidence/modern-prepare.json
    mv evidence/modern-prepare.json reference-evidence/modern-prepare.json
fi
python3 prepare_modern.py
bash rebuild-modern.sh
```

`prepare_modern.py`는 기본 패치를 복사해 `modern-patched`를 만들고, 제거할 클래스의 직접 참조·클래스명 문자열·잔존 네이티브 호출을 확인한다. 삭제 목록과 유지 클래스 해시는 `evidence/modern-prepare.json`에 기록한다.

`rebuild-modern.sh`는 Apktool 빌드, 원본 에셋·리소스 보존 재조립, 정렬, 기본 수정본과 동일한 로컬 키로 서명, 최종 APK 재분해, `verify_modern.py` 실행까지 수행한다. 결과물은 다음과 같다.

- `output/Paladog-2.2.0-modern-offline.apk`
- `output/Paladog-2.2.0-modern-offline.apk.sha256`
- `evidence/modern-static-validation.json`
- `evidence/modern-signature.txt`
- 최종 재분해 디렉터리 `modern-verified-decode`

최신 Android용 검사는 네이티브 라이브러리·ELF·네이티브 메서드/로딩 호출 부재, 잔존 클래스 참조, 유지 코드·에셋의 동일성 및 명시한 텍스트 처리 변경, 최종 Manifest, 서명·ZIP 정렬을 확인한다. 원본 대비 유지한 에셋·리소스 등 4,763개 APK 항목은 바이트 단위로 동일하다. 보고서의 항목 수에는 클래스별 반복 비교도 포함되므로 이를 독립적인 보안 시험 수나 모든 실행 경로를 검증한 수로 해석하지 않는다.

텍스트 호환성을 수정한 현재 APK는 40,227,841바이트이며 SHA-256은 `c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`다. `modern-static-validation.json`과 `.sha256` 파일에 대조해 확인한다. 신규 키로 빌드하면 전달본 해시와 달라진다.

텍스트 수정 전 전체 실행 시험에 사용한 APK의 SHA-256은 `44d1885f7a37bc38acd895a6a793924afc239f3cd56ad3f09f57e697ad55d5c2`다. 기존 `final-api23/RESULT.md`와 `final-api36/RESULT.md`는 그 파일의 기록으로 보존한다. 텍스트 수정본의 확인 결과는 `final-api23-textfix`와 `final-api36-textfix`의 별도 회귀 검사 보고서로 구분하며, 이전 APK의 전체 시험을 새 APK의 시험으로 바꿔 쓰지 않는다. 이전 APK·스크립트·정적 증거의 로컬 보관 경로는 `pre-text-fit-fix/`이며 이 전체 디렉터리는 공개 ZIP에 넣지 않는다.

기본 수정본과 최신 Android용 수정본을 같은 키로 서명하고 versionCode를 올린 것은 일반적인 업데이트 요건을 맞추기 위한 것이다. 기존 저장 데이터의 실제 업데이트·이전 검증과 새 기기에서의 실행 검증은 별도다. 기본 수정본으로 되돌리는 것은 versionCode 하향이므로 일반 업데이트와 동일하게 취급하지 않는다.

## 공개 재현 자료 ZIP의 명시적 포함 목록

아래 경로만 포함하면 원본 APK와 개인키를 제외한 재현 자료를 구성할 수 있다. ZIP 내부 경로도 그대로 보존해 `/workspace` 아래에 풀 수 있게 한다. 파일이 추가되더라도 `paladog-work` 전체를 재귀 압축하지 않는다.

```text
paladog-tools/install.sh
paladog-tools/install-with-runtime.sh
paladog-tools/start-instructions.md
paladog-tools/install-runtime.py
paladog-tools/start-runtime.sh
paladog-tools/lock-runtime-network.sh
paladog-tools/install-modern-runtime.py
paladog-tools/start-modern-runtime.sh
paladog-tools/prepare-modern-runtime.sh
paladog-tools/lock-modern-runtime-network.sh
paladog-tools/runtime/RUNTIME.md
paladog-work/package_repro.py
paladog-work/apply_privacy_patch.py
paladog-work/assemble_minimal_apk.py
paladog-work/verify_static.py
paladog-work/rebuild.sh
paladog-work/sign-build.sh
paladog-work/prepare_modern.py
paladog-work/assemble_modern_apk.py
paladog-work/rebuild-modern.sh
paladog-work/verify_modern.py
paladog-work/output/REPRODUCE.md
paladog-work/output/Paladog-offline-build-notes.md
paladog-work/output/Paladog-modern-build-notes.md
paladog-work/output/Paladog-2.2.0-offline-privacy.apk.sha256
paladog-work/output/Paladog-2.2.0-modern-offline.apk.sha256
paladog-work/evidence/changed-files.json
paladog-work/evidence/privacy.patch
paladog-work/evidence/static-validation.json
paladog-work/evidence/original-signature.txt
paladog-work/evidence/patched-signature.txt
paladog-work/evidence/patch-save-review.md
paladog-work/evidence/control-flow-patch-review.md
paladog-work/evidence/external-paths-review.md
paladog-work/evidence/modern-prepare.json
paladog-work/evidence/modern-static-validation.json
paladog-work/evidence/modern-signature.txt
paladog-work/evidence/modern-native-audit.md
paladog-work/evidence/modern-text-fit-review.md
paladog-work/evidence/android16-compatibility-review.md
paladog-work/evidence/runtime/final-api23/RESULT.md
paladog-work/evidence/runtime/final-api23/03-rankclose-04.png
paladog-work/evidence/runtime/final-api23/10-live-summon.png
paladog-work/evidence/runtime/final-api23/11-exit-dialog.png
paladog-work/evidence/runtime/final-api23/13-relaunch-03.png
paladog-work/evidence/runtime/final-api23/14-resume-06.png
paladog-work/evidence/runtime/final-api23/15-restored-paused.png
paladog-work/evidence/runtime/final-api36/RESULT.md
paladog-work/evidence/runtime/final-api36/01-fresh-title.png
paladog-work/evidence/runtime/final-api36/02-crossarch-load-09.png
paladog-work/evidence/runtime/final-api36/07-summon-and-back.png
paladog-work/evidence/runtime/final-api36/09-native64-relaunch-02.png
paladog-work/evidence/runtime/final-api36/10-native64-resume-04.png
paladog-work/evidence/runtime/final-api36/11-final-quit-dialog.png
paladog-work/evidence/runtime/final-api36-textfix/RESULT.md
paladog-work/evidence/runtime/final-api36-textfix/03-quit-text-fixed.png
paladog-work/evidence/runtime/final-api36-textfix/04-relaunch-02.png
paladog-work/evidence/runtime/final-api36-textfix/07-restored-live-combat.png
paladog-work/evidence/runtime/final-api23-textfix/RESULT.md
paladog-work/evidence/runtime/final-api23-textfix/battle-first.png
paladog-work/evidence/runtime/final-api23-textfix/resume-prompt.png
paladog-work/evidence/runtime/final-api23-textfix/trial2-quit-dialog.png
```

`python3 /workspace/paladog-work/package_repro.py --check`는 위 목록과 스크립트의 명시적 목록이 일치하는지, 모든 파일이 있는지 확인하며 ZIP을 만들지 않는다. 보고서가 확정된 후 `--create`로 실행하면 `output/Paladog-reproducibility.zip`을 새로 만든다. 기존 ZIP은 덮어쓰지 않는다. 보고서·선택 화면을 추가할 때는 이 목록과 스크립트의 목록을 함께 갱신해야 한다.

포함된 검증 보고서·해시 파일은 전달 APK의 기록이다. 새 키로 다시 빌드하면 해당 파일들을 위 명령으로 갱신한다. `external-paths-review.md`는 원본에 대한 조사와 제안 기록이므로 모든 제안이 최종 패치에 적용됐다는 뜻이 아니다. 실제 적용 범위는 최종 빌드 노트와 `privacy.patch`를 기준으로 읽는다.

원본/수정 APK 자체, `private/`, 전체 `decoded`·`patched`·`verified-decode`·`modern-patched`·`modern-verified-decode`·JADX 출력, 도구 캐시, Android 게스트 이미지·저장 데이터·원시 런타임 로그는 이 재현 ZIP에서 제외한다. 초기 `STATUS.md`, `report-review.md`, `setup-validation.log`는 APK 확보 전의 과거 상태를 담아 현재 결과와 혼동될 수 있으므로 제외한다. 최종 실행 결과를 별도로 정리한 보고서는 작성·검토를 마친 뒤 명시적으로 목록에 추가할 수 있다.
