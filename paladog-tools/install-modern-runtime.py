#!/usr/bin/env python3
"""Add verified AOSP Android 16 x86_64 software-emulation tools.

Uses the same preservation and checksum rules as the tested legacy installer.
Official HTTPS repository metadata publishes the pinned SHA-1 checksums.
No emulator or APK is started by this script.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import importlib.util

ROOT = Path('/workspace/paladog-tools/runtime')
spec = importlib.util.spec_from_file_location('preserving_runtime_install', '/workspace/paladog-tools/install-runtime.py')
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)
ARTIFACTS = [
    ('emulator-linux_x64-16428233.zip', 349654171,
     'cd7362ea55dfb86a418958138dc396e74165dd01',
     'https://dl.google.com/android/repository/emulator-linux_x64-16428233.zip', ROOT),
    ('x86_64-36_r02.zip', 844217077,
     '829c076e8ff448a336097ae25a355b495ba36e2c',
     'https://dl.google.com/android/repository/sys-img/android/x86_64-36_r02.zip',
     ROOT / 'system-images/android-36/default'),
]

if __name__ == '__main__':
    installer.DOWNLOADS.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=2) as executor:
        list(executor.map(installer.install, ARTIFACTS))
