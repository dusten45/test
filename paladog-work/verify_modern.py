#!/usr/bin/env python3
"""Independent post-build checks for the ABI-independent offline variant."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root = Path('/workspace/paladog-work')
apk = root/'output/Paladog-2.2.0-modern-offline.apk'
decoded = root/'modern-verified-decode'
prepared = root/'modern-patched'
android = '{http://schemas.android.com/apk/res/android}'
checks = []
def check(name, result):
    assert result, name
    checks.append(name)

removed_libraries = {'lib/armeabi/libAppleJuice.so', 'lib/armeabi/libdes3jni.so', 'lib/armeabi/libsimpleencryption.so', 'lib/armeabi-v7a/libsimpleencryption.so'}
with zipfile.ZipFile(root/'input/original.apk') as original, zipfile.ZipFile(apk) as modern:
    check('ZIP CRC and unique members', modern.testzip() is None and len(modern.namelist()) == len(set(modern.namelist())))
    files = lambda z:{i.filename for i in z.infolist() if not i.is_dir() and not i.filename.startswith('META-INF/')}
    before, after = files(original), files(modern)
    check('Exactly four native libraries removed from APK non-signature members', before - after == removed_libraries and not after - before)
    changed = [name for name in sorted(after) if original.read(name) != modern.read(name)]
    check('Only Manifest and classes.dex changed', changed == ['AndroidManifest.xml', 'classes.dex'])
    preserved = [name for name in sorted(after) if name not in changed]
    check('All original assets, resources, sounds and other retained members preserved byte-for-byte', all(original.read(name) == modern.read(name) for name in preserved))
    check('No ELF image anywhere in APK', not any(modern.read(name).startswith(b'\x7fELF') for name in after))
    check('No remaining native library or ABI member', not any(name.startswith('lib/') or name.endswith('.so') for name in after))
    check('No additional dex, nested APK/JAR/ZIP or executable payload header', all(name == 'classes.dex' or not (modern.read(name).startswith((b'dex\n', b'cdex', b'MZ', b'#!', b'PK\x03\x04', b'\xca\xfe\xba\xbe', b'\xcf\xfa\xed\xfe', b'\xfe\xed\xfa\xcf')) or name.lower().endswith(('.dex', '.apk', '.jar', '.exe', '.sh'))) for name in after))

manifest = ET.parse(decoded/'AndroidManifest.xml').getroot()
app = manifest.find('application')
check('Package remains com.Paladog.KorGG', manifest.get('package') == 'com.Paladog.KorGG')
config = (decoded/'apktool.yml').read_text()
check('Min SDK 7 and target SDK 28 preserved in built manifest metadata', bool(re.search(r'^  minSdkVersion: [\'\"]?7[\'\"]?$', config, re.M)) and bool(re.search(r'^  targetSdkVersion: [\'\"]?28[\'\"]?$', config, re.M)))
check('Version code 26 and version name 2.2.0-offline', bool(re.search(r'^  versionCode: [\'\"]?26[\'\"]?$', config, re.M)) and bool(re.search(r'^  versionName: [\'\"]?2\.2\.0-offline[\'\"]?$', config, re.M)))
perms = [x.get(android+'name') for x in manifest.findall('uses-permission')]
check('Only VIBRATE permission remains', perms == ['android.permission.VIBRATE'])
check('No shared UID, push receiver, service or provider', android+'sharedUserId' not in manifest.attrib and not app.findall('receiver') and not app.findall('service') and not app.findall('provider'))
check('Only launcher and two local dialogs registered', {x.get(android+'name') for x in app.findall('activity')} == {'.Paladog', '.MessageBoxActivity', '.InputActivity'})
check('Only launcher exported', all(x.get(android+'exported') == ('true' if x.get(android+'name') == '.Paladog' else 'false') for x in app.findall('activity')))
check('Backup, debugging and cleartext traffic disabled', all(app.get(android+name) == 'false' for name in ['allowBackup', 'debuggable', 'usesCleartextTraffic']))
check('Legacy game activity resizing disabled', app.get(android+'resizeableActivity') == 'false')
main = next(x for x in app.findall('activity') if x.get(android+'name') == '.Paladog')
check('Orientation and size/layout changes handled by existing game Activity', set(main.get(android+'configChanges').split('|')) == {'keyboardHidden', 'orientation', 'screenSize', 'smallestScreenSize', 'screenLayout'} and main.get(android+'screenOrientation') == 'landscape')
libraries = app.findall('uses-library')
check('Android Apache HTTP compatibility library requested optionally', len(libraries) == 1 and libraries[0].get(android+'name') == 'org.apache.http.legacy' and libraries[0].get(android+'required') == 'false')

preparation = json.loads((root/'evidence/modern-prepare.json').read_text())
removed = {name[:-6] for name in preparation['removed_classes']}
remaining = {p.relative_to(decoded/'smali').as_posix():p for p in (decoded/'smali').rglob('*.smali')}
check('928 unused classes removed and 1176 classes retained', len(preparation['removed_classes']) == 928 and len(remaining) == 1176)
check('Built class inventory equals prepared class inventory', set(remaining) == set(preparation['retained_class_sha256']))
def canonical_smali(text):
    # Apktool renumbers labels after prior privacy edits. Compare every opcode,
    # register, operand and branch target while identifying targets by position.
    # Debug metadata and label spelling do not change bytecode behavior.
    result=[]
    method=[]
    labels={}
    data_block=False
    in_method=False
    def finish_method():
        for item in method:
            result.append(re.sub(r':[A-Za-z_][A-Za-z_0-9]*', lambda match: '@target_'+str(labels[match.group()]) if match.group() in labels else match.group(), item))
    for line in text.splitlines():
        line=line.strip()
        if not line or line.startswith(('#', '.line ', '.source ', '.prologue', '.epilogue', '.local ', '.end local ', '.restart local ', '.param ', '.end param')):
            continue
        if line.startswith('.method '):
            method=[]
            labels={}
            data_block=False
            in_method=True
        line=line.encode('ascii', 'backslashreplace').decode('ascii')
        if line.startswith(':') and not data_block:
            labels[line]=len(method)
            continue
        method.append(line)
        if line.startswith(('.packed-switch ', '.sparse-switch', '.array-data ')):
            data_block=True
        if line.startswith(('.end packed-switch', '.end sparse-switch', '.end array-data')):
            data_block=False
        if line == '.end method':
            finish_method()
            method=[]
            labels={}
            in_method=False
        elif not in_method:
            result.extend(method)
            method=[]
    # DEX places direct/private methods before virtual methods; a source patch
    # may append a new private method at the end of the Smali file.
    header=[]
    methods={}
    current=None
    for item in result:
        if item.startswith('.method '):
            current=item
            assert current not in methods, 'Duplicate method signature'
            methods[current]=[]
        if current is None:
            header.append(item)
        else:
            methods[current].append(item)
            if item == '.end method':
                current=None
    return header, sorted((key, value) for key, value in methods.items())
for relative, path in remaining.items():
    source = (prepared/'smali'/relative).read_bytes()
    check('Retained bytecode source unchanged: '+relative, hashlib.sha256(source).hexdigest() == preparation['retained_class_sha256'][relative])
    text = path.read_text()
    assert not re.search(r'^\.method[^\n]*\bnative\b|Ljava/lang/(?:System|Runtime);->load(?:Library)?\(', text, re.M), 'Native dependency: ' + relative
    assert not set(re.findall(r'L([^;\s]+);', text)) & removed, 'Dangling removed class reference: '+relative
    assert not re.search(r'const-string[^\n]*"(?:com[./](?:appleJuice|applePay|kakao|tenpay)|comm[./]applePay|oicq[./]|com[./]SPC[./]TestAJService)',text), 'Dynamic SDK class literal: '+relative
    assert canonical_smali(text) == canonical_smali(source.decode()), 'Rebuilt Smali differs from preserved baseline: '+relative
checks.append('All rebuilt retained Smali instructions and branch targets match prepared sources, excluding debug metadata and label spelling')
checks.append('Zero native methods, native loads, dangling SDK descriptors or SDK class-name literals')
renderer_relative = 'org/cocos2d/opengl/CCTexture2D.smali'
changed_classes = [relative for relative in remaining if (root/'patched/smali'/relative).read_bytes() != (prepared/'smali'/relative).read_bytes()]
check('1175 classes unchanged; only the text renderer differs from privacy baseline', changed_classes == [renderer_relative])
check('All game, save and other retained classes preserved from reviewed privacy patch', all((root/'patched/smali'/relative).read_bytes() == (prepared/'smali'/relative).read_bytes() for relative in remaining if relative != renderer_relative))
renderer_text = (prepared/'smali'/renderer_relative).read_text()
baseline_renderer = (root/'patched/smali'/renderer_relative).read_text()
fit_re = re.compile(r'    # Modern font compatibility: preserve an already fitting whole line\.\n(?P<code>.*?)    :modern_wrap_fallback\n\n', re.S)
fit_match = fit_re.search(renderer_text)
check('Renderer fallback and every other method byte-for-byte unchanged', fit_match is not None and fit_re.sub('',renderer_text,count=1) == baseline_renderer)
fit_instructions = [line.strip() for line in fit_match['code'].splitlines() if line.strip()]
check('Fitting whole line is measured, compared with <= and returned unchanged as singleton', fit_instructions == [
    'invoke-virtual {p1, p2}, Landroid/graphics/Paint;->measureText(Ljava/lang/String;)F',
    'move-result v0', 'cmpg-float v0, v0, p3', 'if-gtz v0, :modern_wrap_fallback',
    'new-instance v0, Ljava/util/ArrayList;', 'invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V',
    'invoke-virtual {v0, p2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z', 'return-object v0'])
check('Longer or unordered widths branch to original word-wrap algorithm', '    :modern_wrap_fallback\n\n    .prologue\n    .line 471\n    move v1, p3\n' in renderer_text and 'invoke-virtual {p2, v7}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;' in renderer_text)
check('Original licensing policy remains unchanged', (root/'decoded/smali/com/google/android/vending/licensing/ServerManagedPolicy.smali').read_bytes() == (prepared/'smali/com/google/android/vending/licensing/ServerManagedPolicy.smali').read_bytes())

tools = Path('/workspace/paladog-tools/android/build-tools/35.0.0')
signature = subprocess.run([str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True, capture_output=True, check=True).stdout
(root/'evidence/modern-signature.txt').write_text(signature)
check('V1 and V2 signatures verify', all(x in signature for x in ['Verifies', 'Verified using v1 scheme (JAR signing): true', 'Verified using v2 scheme (APK Signature Scheme v2): true']))
baseline = (root/'evidence/patched-signature.txt').read_text()
certificate = lambda value:re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', value).group(1)
check('Modern and previous privacy APK use the same private signing certificate', certificate(signature) == certificate(baseline))
subprocess.run([str(tools/'zipalign'), '-c', '4', str(apk)], check=True)
checks.append('ZIP alignment verifies')
report = {'apk':str(apk), 'bytes':apk.stat().st_size, 'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(), 'checks_passed':checks, 'preserved_entries':len(preserved), 'removed_libraries':sorted(removed_libraries), 'remaining_classes':len(remaining), 'unchanged_retained_classes':1175, 'changed_retained_class':renderer_relative, 'renderer_change':'WrapText returns the exact original string as one line when Paint.measureText(text) <= width; otherwise unchanged wordwrap.', 'native_declarations':0, 'native_loads':0, 'elf_files':0, 'permissions':perms, 'signer_sha256':certificate(signature), 'runtime_status':'Not established by this static report; test the focused text-fit fix separately. Earlier 44d runtime evidence is historical.'}
(root/'evidence/modern-static-validation.json').write_text(json.dumps(report, indent=2))
(root/'output/Paladog-2.2.0-modern-offline.apk.sha256').write_text(report['sha256']+'  '+apk.name+'\n')
print(json.dumps({key:report[key] for key in ['apk','bytes','sha256','preserved_entries','remaining_classes','elf_files']}, indent=2))
print('Checks passed:',len(checks))
