#!/usr/bin/env bash
set -euo pipefail
umask 077
work_root=/workspace/paladog-work
build_tools=/workspace/paladog-tools/android/build-tools/35.0.0
mkdir -p "$work_root/private"
chmod 700 "$work_root/private"
if [[ ! -f "$work_root/private/offline-signing.p12" ]]; then
  python3 - <<'PY'
from pathlib import Path
import secrets
p=Path('/workspace/paladog-work/private/keystore-password')
with p.open('x') as f:
    f.write(secrets.token_hex(32) + '\n')
p.chmod(0o600)
PY
  keytool -genkeypair -storetype PKCS12 -keystore "$work_root/private/offline-signing.p12" -alias paladog-local -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Paladog Local Privacy Build, O=Personal Offline Build' -storepass:file "$work_root/private/keystore-password" -keypass:file "$work_root/private/keystore-password" -noprompt
fi
"$build_tools/zipalign" -f -p 4 "$work_root/output/paladog-offline-unsigned.apk" "$work_root/output/paladog-offline-aligned.apk"
"$build_tools/apksigner" sign --ks "$work_root/private/offline-signing.p12" --ks-key-alias paladog-local --ks-pass "file:$work_root/private/keystore-password" --min-sdk-version 7 --out "$work_root/output/Paladog-2.2.0-offline-privacy.apk" "$work_root/output/paladog-offline-aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$work_root/output/Paladog-2.2.0-offline-privacy.apk" > "$work_root/evidence/patched-signature.txt"
"$build_tools/zipalign" -c -p 4 "$work_root/output/Paladog-2.2.0-offline-privacy.apk"
sha256sum "$work_root/output/Paladog-2.2.0-offline-privacy.apk"
