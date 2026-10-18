#!/usr/bin/env bash
set -euo pipefail
cd /workspace/paladog-work
/workspace/paladog-tools/bin/apktool b -p /workspace/paladog-tools/framework patched -o output/paladog-apktool-unsigned.apk
python3 assemble_minimal_apk.py
bash sign-build.sh
