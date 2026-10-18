#!/usr/bin/env python3
"""Use rebuilt Manifest/DEX only; preserve original resources, assets and libraries."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile

root=Path('/workspace/paladog-work')
with zipfile.ZipFile(root/'input/original.apk') as original, zipfile.ZipFile(root/'output/paladog-apktool-unsigned.apk') as rebuilt:
    assert original.testzip() is None and rebuilt.testzip() is None
    replacements={name:rebuilt.read(name) for name in ['AndroidManifest.xml','classes.dex']}
    removed={'META-INF/CERT.SF','META-INF/CERT.RSA','META-INF/MANIFEST.MF'}
    assert removed.issubset(original.namelist())
    with zipfile.ZipFile(root/'output/paladog-offline-unsigned.apk','w') as output:
        for item in original.infolist():
            if item.filename in removed:
                continue
            data=replacements.get(item.filename)
            if data is None:
                data=original.read(item.filename)
            output.writestr(copy.copy(item),data)
print('Assembled minimal APK: replaced Manifest/DEX and removed old signature only.')
