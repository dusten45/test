#!/usr/bin/env python3
import difflib
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path('/workspace/paladog-work')
apk=root/'output/Paladog-2.2.0-offline-privacy.apk'
checks=[]
def check(name, result):
    assert result, name
    checks.append(name)

with zipfile.ZipFile(root/'input/original.apk') as a, zipfile.ZipFile(apk) as b:
    check('APK ZIP CRC', b.testzip() is None)
    check('No duplicate ZIP members',len(b.namelist())==len(set(b.namelist())))
    ordinary=lambda n:not n.startswith('META-INF/')
    aa={n for n in a.namelist() if ordinary(n)}
    bb={n for n in b.namelist() if ordinary(n)}
    check('Original non-signature member set preserved',aa==bb)
    changed=[n for n in sorted(aa) if a.read(n)!=b.read(n)]
    check('Only Manifest and DEX changed inside APK',changed==['AndroidManifest.xml','classes.dex'])
    protected=[n for n in sorted(aa) if n not in changed]
    check('All other APK contents byte-for-byte preserved',all(a.read(n)==b.read(n) for n in protected))
    native=[{'path':n,'sha256':hashlib.sha256(b.read(n)).hexdigest(),'elf_class':b.read(n)[4],'machine':int.from_bytes(b.read(n)[18:20],'little')} for n in sorted(bb) if n.endswith('.so')]
    check('Native ABI inventory: ARM32 libraries retained',all(n['elf_class']==1 and n['machine']==40 for n in native))

decoded=root/'verified-decode'
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(decoded/'AndroidManifest.xml').getroot()
perms=[x.get(ns+'name') for x in manifest.findall('uses-permission')]
check('Only VIBRATE permission remains',perms==['android.permission.VIBRATE'])
app=manifest.find('application')
check('No push receiver or service',not app.findall('receiver') and not app.findall('service'))
check('No shared UID',ns+'sharedUserId' not in manifest.attrib)
check('Automatic cloud backup disabled',app.get(ns+'allowBackup')=='false')
check('Cleartext explicitly disabled',app.get(ns+'usesCleartextTraffic')=='false')
check('Only launcher exported',all(x.get(ns+'exported')==('true' if x.get(ns+'name')=='.Paladog' else 'false') for x in app.findall('activity')))

def code(rel):return (decoded/'smali'/rel).read_text()
def body(text,sig):
    m=re.search(r'^\.method '+re.escape(sig)+r'\n(.*?)^\.end method',text,re.M|re.S)
    assert m,sig
    return m.group(1)
p=code('com/Paladog/KorGG/Paladog.smali')
check('No telephony, Android-ID, billing binding or GCM API invocation in main Activity',not re.search(r'invoke-.*(?:Landroid/telephony/TelephonyManager|Settings\$Secure|->bindService\(|GCMRegistrar)',p))
check('UDID and phone use empty strings',p.count('const-string v4, ""')>=4 and '01012345678' not in p)
check('Billing connection cleared before initialization', 'iput-object v3, p0, Lcom/Paladog/KorGG/Paladog;->mServiceConn' in body(p,'protected onCreate(Landroid/os/Bundle;)V'))
check('Process enumeration removed',not re.search(r'getRunningAppProcesses|Runtime;->exec',body(p,'public checkCheatApp()Z')))
check('Existing license initialization still invoked','->checkAccess(' in body(p,'public Google_LVL()Z'))
check('New offline notices use registered local dialog','->MessageBoxActivity(' in body(p,'protected BuyCashItem(Ljava/lang/String;)V'))
for sig in ['public startActivity(Landroid/content/Intent;)V','public startActivityForResult(Landroid/content/Intent;I)V']:
    check('Explicit local component filter: '+sig, '->getComponent()' in body(p,sig) and '->equals(' in body(p,sig))
l=code('com/SPC/LoadingLayer.smali')
proc=body(l,'public LoadingProc(F)V')
check('Consent prompts and registration requests physically removed','zamburgers' not in l and 'SMS ' not in l and 'naver_agree' not in l and '->DownloadHtml(' not in proc)
check('Core startup and battle recovery retained',all(x in proc for x in ['->InitAfterDB()','->CheckNeedLoadBattle()','->DBInfoCheckSum()','->Google_LVL()']))
lib=code('com/common/Library.smali')
for sig in ['public DownloadHtml(Ljava/lang/String;)Ljava/lang/String;','public DownloadHtml_Apache(Ljava/lang/String;)Ljava/lang/String;','public GetServerXML(I)V','public HttpFileDownload(Ljava/lang/String;Ljava/lang/String;)V','public HttpFileUpload(Ljava/lang/String;Ljava/lang/String;)V','public networkCheck()Z','public rootingCheck()Z']:
    check('Offline helper has no side effects: '+sig,'invoke-' not in body(lib,sig))

changed_files=json.loads((root/'evidence/changed-files.json').read_text())
actual=[str(p.relative_to(root/'decoded')) for p in (root/'decoded').rglob('*') if p.is_file() and (root/'patched'/p.relative_to(root/'decoded')).read_bytes()!=p.read_bytes()]
check('Only five planned decoded files modified',sorted(actual)==sorted(changed_files))
savepaths=list((root/'decoded/smali/com/my/Struct').glob('GAMESAVE*.smali'))+[root/'decoded/smali/com/my/Struct/LOCALRANKSAVE.smali',root/'decoded/smali/com/common/WCEncrypt.smali',root/'decoded/smali/com/common/AppDelegate.smali']
check('Save serializers, encryption and AppDelegate unchanged',all(p.read_bytes()==(root/'patched'/p.relative_to(root/'decoded')).read_bytes() for p in savepaths))
check('Original license policy unchanged',(root/'decoded/smali/com/google/android/vending/licensing/ServerManagedPolicy.smali').read_bytes()==(root/'patched/smali/com/google/android/vending/licensing/ServerManagedPolicy.smali').read_bytes())
sig=(root/'evidence/patched-signature.txt').read_text()
check('APK v1 and v2 signatures verify',all(x in sig for x in ['Verifies','Verified using v1 scheme (JAR signing): true','Verified using v2 scheme (APK Signature Scheme v2): true']))
check('Output certificate differs from public test key','a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc' not in sig)
diff=''.join(''.join(difflib.unified_diff((root/'decoded'/p).read_text().splitlines(True),(root/'patched'/p).read_text().splitlines(True),fromfile='original/'+p,tofile='patched/'+p)) for p in changed_files)
(root/'evidence/privacy.patch').write_text(diff)
report={'apk':str(apk),'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'checks_passed':checks,'unchanged_non_signature_entries':len(protected),'changed_non_signature_entries':changed,'permissions':perms,'native_libraries':native,'runtime_validation':'reported separately; static checks do not establish gameplay or malware absence'}
(root/'evidence/static-validation.json').write_text(json.dumps(report,indent=2,ensure_ascii=False))
print(json.dumps({'checks_passed':len(checks),'unchanged_entries':len(protected),'bytes':report['bytes'],'sha256':report['sha256']},indent=2))
