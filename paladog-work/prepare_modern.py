#!/usr/bin/env python3
"""Create the modern ABI-independent variant from the reviewed privacy patch."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path('/workspace/paladog-work')
SOURCE = ROOT / 'patched'
TARGET = ROOT / 'modern-patched'
SDK = ('com/appleJuice/', 'com/applePay/', 'comm/applePay/', 'com/kakao/', 'com/tenpay/', 'oicq/')
UNUSED = 'com/SPC/TestAJService.smali'
LIBRARIES = ('lib/armeabi/libAppleJuice.so', 'lib/armeabi/libdes3jni.so', 'lib/armeabi/libsimpleencryption.so', 'lib/armeabi-v7a/libsimpleencryption.so')
assert SOURCE.is_dir(), 'Run the original privacy patch first'
assert not TARGET.exists(), 'Refusing to overwrite existing modern-patched; preserve it before a new build'
assert not (ROOT/'evidence/modern-prepare.json').exists(), 'Refusing to overwrite preparation evidence'
all_classes = sorted((SOURCE/'smali').rglob('*.smali'))
removed = [p for p in all_classes if p.relative_to(SOURCE/'smali').as_posix().startswith(SDK) or p.relative_to(SOURCE/'smali').as_posix() == UNUSED]
assert len(all_classes) == 2104 and len(removed) == 928, 'Unexpected baseline class inventory'
removed_names = {p.relative_to(SOURCE/'smali').with_suffix('').as_posix() for p in removed}
for p in all_classes:
    if p in removed:
        continue
    text = p.read_text()
    assert not (set(re.findall(r'L([^;\s]+);', text)) & removed_names), 'Remaining class refers to removed SDK: ' + str(p)
    assert not re.search(r'^\.method[^\n]*\bnative\b|Ljava/lang/(?:System|Runtime);->load(?:Library)?\(', text, re.M), 'Unexpected remaining native dependency'
    assert not any(name in text or name.replace('/', '.') in text for name in removed_names), 'Remaining literal references removed class'

shutil.copytree(SOURCE, TARGET, ignore=shutil.ignore_patterns('build', 'dist'))
for p in removed:
    (TARGET/p.relative_to(SOURCE)).unlink()
for namespace in SDK:
    shutil.rmtree(TARGET/'smali'/namespace)
for relative in LIBRARIES:
    path = TARGET/relative
    assert path.read_bytes().startswith(b'\x7fELF'), 'Unexpected native library'
    path.unlink()
for directory in sorted((TARGET/'lib').rglob('*'), reverse=True):
    if directory.is_dir() and not any(directory.iterdir()):
        directory.rmdir()
if not any((TARGET/'lib').iterdir()):
    (TARGET/'lib').rmdir()

android = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
manifest = ET.parse(TARGET/'AndroidManifest.xml')
element = manifest.getroot()
element.set(android+'versionCode', '26')
element.set(android+'versionName', '2.2.0-offline')
sdk = element.find('uses-sdk')
if sdk is None:
    sdk = ET.Element('uses-sdk')
    element.insert(0, sdk)
sdk.set(android+'minSdkVersion', '7')
sdk.set(android+'targetSdkVersion', '28')
app = element.find('application')
assert app is not None
app.set(android+'resizeableActivity', 'false')
main = next(activity for activity in app.findall('activity') if activity.get(android+'name') == '.Paladog')
main.set(android+'configChanges', 'keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout')
assert not app.findall('uses-library')
ET.SubElement(app, 'uses-library', {android+'name':'org.apache.http.legacy', android+'required':'false'})
ET.indent(manifest, space='  ')
manifest.write(TARGET/'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)

config = (TARGET/'apktool.yml').read_text()
assert config.count('  minSdkVersion: 7\n') == 1 and 'targetSdkVersion:' not in config
assert config.count('  versionCode: 25\n') == 1 and config.count('  versionName: 2.2.0\n') == 1
config = config.replace('  minSdkVersion: 7\n', '  minSdkVersion: 7\n  targetSdkVersion: 28\n')
config = config.replace('  versionCode: 25\n', '  versionCode: 26\n').replace('  versionName: 2.2.0\n', '  versionName: 2.2.0-offline\n')
(TARGET/'apktool.yml').write_text(config)
retained = [p for p in all_classes if p not in removed]
assert all(p.read_bytes() == (TARGET/p.relative_to(SOURCE)).read_bytes() for p in retained)
renderer = TARGET/'smali/org/cocos2d/opengl/CCTexture2D.smali'
renderer_source = renderer.read_text()
method_marker = '.method protected WrapText(Landroid/graphics/Paint;Ljava/lang/String;F)Ljava/util/ArrayList;'
assert renderer_source.count(method_marker) == 1
method_start = renderer_source.index(method_marker)
insertion = renderer_source.index('    .prologue\n', method_start)
# Auto-sized labels have one line of bitmap height. A string that already fits
# whole-string Paint measurement must not be rewrapped using summed word widths
# (modern font shaping can make those different). Longer text keeps old wrapping.
# cmpg treats NaN as greater, so only the requested finite <= case uses this path.
fit_prefix = '''    # Modern font compatibility: preserve an already fitting whole line.
    invoke-virtual {p1, p2}, Landroid/graphics/Paint;->measureText(Ljava/lang/String;)F
    move-result v0
    cmpg-float v0, v0, p3
    if-gtz v0, :modern_wrap_fallback
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    invoke-virtual {v0, p2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    return-object v0
    :modern_wrap_fallback

'''
renderer.write_text(renderer_source[:insertion] + fit_prefix + renderer_source[insertion:])
unchanged = [p for p in retained if p.relative_to(SOURCE/'smali').as_posix() != 'org/cocos2d/opengl/CCTexture2D.smali']
assert len(unchanged) == 1175 and all(p.read_bytes() == (TARGET/p.relative_to(SOURCE)).read_bytes() for p in unchanged)
report = {'source': str(SOURCE), 'target': str(TARGET), 'source_classes': len(all_classes), 'removed_classes': [p.relative_to(SOURCE/'smali').as_posix() for p in removed], 'retained_classes': len(retained), 'unchanged_retained_classes':len(unchanged), 'renderer_fix': {'class':'org/cocos2d/opengl/CCTexture2D.smali', 'method':method_marker, 'change':'When Paint.measureText(text) <= width return the original string as one line; otherwise preserve original word wrapping.'}, 'removed_libraries': list(LIBRARIES), 'retained_class_sha256': {p.relative_to(SOURCE/'smali').as_posix(): hashlib.sha256((TARGET/p.relative_to(SOURCE)).read_bytes()).hexdigest() for p in retained}, 'baseline_class_sha256':{p.relative_to(SOURCE/'smali').as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in retained}, 'changed_remaining_files': ['AndroidManifest.xml', 'apktool.yml','smali/org/cocos2d/opengl/CCTexture2D.smali'], 'target_sdk': 28, 'min_sdk': 7, 'version_code': 26, 'version_name':'2.2.0-offline'}
(ROOT/'evidence/modern-prepare.json').write_text(json.dumps(report, indent=2))
print(f'Prepared modern variant: {len(removed)} unused classes and four native libraries removed; {len(unchanged)} retained classes byte-identical plus one focused text-fit renderer fix.')
