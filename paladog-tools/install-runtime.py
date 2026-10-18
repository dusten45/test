#!/usr/bin/env python3
"""Install official ARM32 Android emulation tools without executing any APK.

Checksums below are the SHA-1 values published in Google's HTTPS repository XML.
TLS verification remains enabled. Tools and images retain their license notices.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import os
import shutil
import urllib.request
import zipfile

ROOT = Path('/workspace/paladog-tools/runtime')
DOWNLOADS = ROOT / 'downloads'
ARTIFACTS = [
    # Modern emulator 37 rejects ARM32 despite shipping qemu-system-armel.
    # This official archived SDK contains the classic ARM emulator engine.
    ('tools_r25.2.5-linux.zip', 277894900,
     '72df3aa1988c0a9003ccdfd7a13a7b8bd0f47fc1',
     'https://dl.google.com/android/repository/tools_r25.2.5-linux.zip', ROOT),
    ('platform-tools_r37.0.1-linux.zip', 9054187,
     '477254aa5f903c15cf51001717bdf347fb6b53e0',
     'https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip', ROOT),
    ('armeabi-v7a-23_r06.zip', 238333358,
     '7cf2ad756e54a3acfd81064b63cb0cb9dff2798d',
     'https://dl.google.com/android/repository/sys-img/android/armeabi-v7a-23_r06.zip',
     ROOT / 'system-images/android-23/default'),
]

def digest(path):
    h = hashlib.sha1()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def install(item):
    name, size, checksum, url, dest = item
    archive = DOWNLOADS / name
    if archive.is_symlink():
        raise RuntimeError(f'Refusing a cached archive symlink: {archive}')
    if archive.exists():
        if not archive.is_file() or archive.stat().st_size != size or digest(archive) != checksum:
            raise RuntimeError(f'Existing cached archive differs; preserved unchanged: {archive}')
    else:
        temp = archive.with_suffix('.partial')
        # Exclusive creation preserves incomplete downloads for inspection.
        with temp.open('xb') as out, urllib.request.urlopen(url, timeout=90) as response:
            for chunk in iter(lambda: response.read(1024 * 1024), b''):
                out.write(chunk)
                if out.tell() > size:
                    raise RuntimeError(f'Unexpected archive length: {name}')
        if temp.stat().st_size != size or digest(temp) != checksum:
            raise RuntimeError(f'Archive integrity verification failed: {name}')
        # An exclusive link also refuses to replace a concurrently created cache.
        os.link(temp, archive)
        temp.unlink()
    print(f'Verified {name}', flush=True)
    dest.mkdir(parents=True, exist_ok=True)
    pending = []
    preserved = 0
    with zipfile.ZipFile(archive) as z:
        # Validate every existing package member before creating any missing
        # member. Existing content and permissions are never overwritten.
        for entry in z.infolist():
            target = dest / entry.filename
            for component in [target, *target.parents]:
                if component == dest.parent:
                    break
                if component.is_symlink():
                    raise RuntimeError(f'Refusing an installed package symlink: {component}')
            target = target.resolve()
            if not target.is_relative_to(dest.resolve()):
                raise RuntimeError('Unsafe archive path')
            if ((entry.external_attr >> 16) & 0o170000) == 0o120000:
                raise RuntimeError('Unexpected symlink in archive')
            if target.exists():
                if entry.is_dir():
                    if not target.is_dir():
                        raise RuntimeError(f'Existing package path differs: {target}')
                else:
                    if not target.is_file() or target.stat().st_size != entry.file_size:
                        raise RuntimeError(f'Existing package file differs; preserved unchanged: {target}')
                    with z.open(entry) as expected, target.open('rb') as actual:
                        while True:
                            chunk = expected.read(1024 * 1024)
                            if chunk != actual.read(len(chunk)):
                                raise RuntimeError(f'Existing package file differs; preserved unchanged: {target}')
                            if not chunk:
                                break
                    preserved += 1
            else:
                pending.append((entry, target))
        installed = 0
        for entry, target in pending:
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with z.open(entry) as source, target.open('xb') as output:
                    shutil.copyfileobj(source, output, length=1024 * 1024)
                mode = (entry.external_attr >> 16) & 0o777
                if mode:
                    os.chmod(target, mode)
                installed += 1
    print(f'Ready {name}: preserved {preserved} matching files, installed {installed} missing files', flush=True)

if __name__ == '__main__':
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=3) as executor:
        list(executor.map(install, ARTIFACTS))
