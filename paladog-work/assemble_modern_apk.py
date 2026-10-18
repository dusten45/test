#!/usr/bin/env python3
"""Preserve original resources/assets while replacing Manifest/DEX and omitting ARM SDKs."""
import copy
from pathlib import Path
import zipfile

root = Path('/workspace/paladog-work')
removed_libraries = {'lib/armeabi/libAppleJuice.so', 'lib/armeabi/libdes3jni.so', 'lib/armeabi/libsimpleencryption.so', 'lib/armeabi-v7a/libsimpleencryption.so'}
with zipfile.ZipFile(root/'input/original.apk') as original, zipfile.ZipFile(root/'output/paladog-modern-apktool-unsigned.apk') as rebuilt:
    assert original.testzip() is None and rebuilt.testzip() is None
    replacements = {name:rebuilt.read(name) for name in ['AndroidManifest.xml', 'classes.dex']}
    signatures = {'META-INF/CERT.SF', 'META-INF/CERT.RSA', 'META-INF/MANIFEST.MF'}
    assert (removed_libraries | signatures).issubset(original.namelist())
    with zipfile.ZipFile(root/'output/paladog-modern-unsigned.apk', 'w') as output:
        for item in original.infolist():
            if item.filename in removed_libraries | signatures or (item.is_dir() and item.filename.startswith('lib/')):
                continue
            data = replacements.get(item.filename)
            if data is None:
                data = original.read(item.filename)
            output.writestr(copy.copy(item), data)
print('Modern APK repacked: all original assets/resources preserved, four native libraries omitted.')
