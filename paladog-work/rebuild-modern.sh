#!/usr/bin/env bash
set -euo pipefail
umask 077
modern_root=/workspace/paladog-work
modern_tools=/workspace/paladog-tools/android/build-tools/35.0.0
cd "$modern_root"
[[ -f private/offline-signing.p12 && -f private/keystore-password ]]
[[ -d modern-patched ]]
/workspace/paladog-tools/bin/apktool b -p /workspace/paladog-tools/framework modern-patched -o output/paladog-modern-apktool-unsigned.apk > evidence/modern-build.log 2>&1
python3 assemble_modern_apk.py
"$modern_tools/zipalign" -f 4 output/paladog-modern-unsigned.apk output/paladog-modern-aligned.apk
"$modern_tools/apksigner" sign --ks private/offline-signing.p12 --ks-pass file:private/keystore-password --min-sdk-version 7 --v1-signing-enabled true --v2-signing-enabled true --out output/Paladog-2.2.0-modern-offline.apk output/paladog-modern-aligned.apk
"$modern_tools/apksigner" verify --verbose --print-certs output/Paladog-2.2.0-modern-offline.apk > evidence/modern-signature.txt
"$modern_tools/zipalign" -c 4 output/Paladog-2.2.0-modern-offline.apk
[[ ! -e modern-verified-decode ]] || { echo 'Preserve previous modern-verified-decode before rerunning decode.' >&2; exit 1; }
/workspace/paladog-tools/bin/apktool d -p /workspace/paladog-tools/framework output/Paladog-2.2.0-modern-offline.apk -o modern-verified-decode > evidence/modern-decode.log 2>&1
python3 verify_modern.py
