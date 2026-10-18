#!/usr/bin/env python3
"""Deterministic patch of the verified original decode; never edits the input APK."""
import difflib
import hashlib
import json
import re
import shutil
from pathlib import Path
import xml.etree.ElementTree as ET

base = Path('/workspace/paladog-work')
original = base / 'decoded'
target = base / 'patched'
expected = 'db9d5c399d7f436a27dba28eebd66771fdc29c812c0e06008c67409bd595a6e1'
assert hashlib.sha256((base/'input/original.apk').read_bytes()).hexdigest() == expected
assert not target.exists(), 'Preserve existing patch work; choose a new directory before rerunning.'
shutil.copytree(original, target, ignore=shutil.ignore_patterns('build', 'dist'))
changes = []

def edit(rel, fn):
    path = target / rel
    before = path.read_text()
    after = fn(before)
    assert before != after, 'No change: ' + rel
    path.write_text(after)
    changes.append(rel)

def replace(s, old, new, n=1):
    assert s.count(old) == n, (old[:100], s.count(old), n)
    return s.replace(old, new)

def method(s, signature, fn):
    pattern = re.compile(r'^\.method ' + re.escape(signature) + r'\n.*?^\.end method', re.M | re.S)
    matches = list(pattern.finditer(s))
    assert len(matches) == 1, signature
    m = matches[0]
    return s[:m.start()] + fn(m.group()) + s[m.end():]

def stub(s, signature, body, locals_=1):
    return method(s, signature, lambda _: '.method ' + signature + '\n    .locals ' + str(locals_) + '\n' + body + '\n.end method')

def paladog(s):
    def startup(m):
        a = m.index('    const-string v3, "phone"')
        b = m.index('    .line 147', a)
        m = m[:a] + '    const/4 v1, 0x0\n\n' + m[b:]
        for api in ['getDeviceId', 'getLine1Number']:
            m = replace(m, f'    invoke-virtual {{v1}}, Landroid/telephony/TelephonyManager;->{api}()Ljava/lang/String;\n\n    move-result-object v4', '    const-string v4, ""')
        m = replace(m, 'const-string v4, "01012345678"', 'const-string v4, ""', 2)
        a = m.index('    .line 240\n')
        b = m.index('    .line 245\n', a)
        m = m[:a] + '''    # No billing binding. Null also keeps onDestroy from unbinding it.
    const/4 v3, 0x0
    iput-object v3, p0, Lcom/Paladog/KorGG/Paladog;->mServiceConn:Landroid/content/ServiceConnection;

''' + m[b:]
        return m
    s = method(s, 'protected onCreate(Landroid/os/Bundle;)V', startup)
    def license_id(m):
        a = m.index('    invoke-virtual {p0}, Lcom/Paladog/KorGG/Paladog;->getContentResolver()')
        b = m.index('    .line 473', a)
        return m[:a] + '    const-string v0, "paladog-local-offline"\n\n' + m[b:]
    s = method(s, 'public Google_LVL()Z', license_id)
    for sig in ['public registerGcm()V', 'public DownloadCheatFile()V']:
        s = stub(s, sig, '    return-void', 0)
    s = stub(s, 'public checkCheatApp()Z', '    const/4 v0, 0x0\n    return v0')
    s = stub(s, 'public DownloadHtml(Ljava/lang/String;)Ljava/lang/String;', '    const/4 v0, 0x0\n    return-object v0')
    s = stub(s, 'protected BuyCashItem(Ljava/lang/String;)V', '''    invoke-static {}, Lcom/common/AppDelegate;->sharedAppDelegate()Lcom/common/AppDelegate;
    move-result-object v0
    const/4 v1, 0x0
    iput-boolean v1, v0, Lcom/common/AppDelegate;->g_bBuying:Z
    iput-boolean v1, v0, Lcom/common/AppDelegate;->g_bPurchaseOK:Z
    const-string v0, "오프라인 버전에서는 결제를 사용할 수 없습니다."
    const-string v1, "알림"
    invoke-virtual {p0, v1, v0}, Lcom/Paladog/KorGG/Paladog;->MessageBoxActivity(Ljava/lang/String;Ljava/lang/String;)V
    return-void''', 2)
    s = stub(s, 'public OpenFacebookActivity(I)V', '''    const/4 v0, 0x0
    iput-boolean v0, p0, Lcom/Paladog/KorGG/Paladog;->m_bLoginTrying:Z
    invoke-static {}, Lcom/common/AppDelegate;->sharedAppDelegate()Lcom/common/AppDelegate;
    move-result-object v0
    invoke-virtual {v0}, Lcom/common/AppDelegate;->FacebookResetRequestKind()V
    return-void''')
    s = stub(s, 'public ServerSend_ErrorMessageBoxActivity(Ljava/lang/String;Ljava/lang/String;)V', '''    const-string v0, "알림"
    invoke-virtual {p0, v0, p1}, Lcom/Paladog/KorGG/Paladog;->MessageBoxActivity(Ljava/lang/String;Ljava/lang/String;)V
    return-void''', 1)
    # Restrict app-driven activity launches to its own explicit local components.
    # This closes browser/SMS delegation in game code without changing game UI classes.
    for signature, supercall in [
        ('public startActivity(Landroid/content/Intent;)V', 'invoke-super {p0, p1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V'),
        ('public startActivityForResult(Landroid/content/Intent;I)V', 'invoke-super {p0, p1, p2}, Landroid/app/Activity;->startActivityForResult(Landroid/content/Intent;I)V')]:
        assert '.method ' + signature not in s
        s += '\n.method ' + signature + '''
    .locals 2
    invoke-virtual {p1}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;
    move-result-object v0
    if-eqz v0, :offline_return
    invoke-virtual {v0}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;
    move-result-object v0
    invoke-virtual {p0}, Lcom/Paladog/KorGG/Paladog;->getPackageName()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :offline_return
    ''' + supercall + '''
    :offline_return
    return-void
.end method
'''
    return s

edit('smali/com/Paladog/KorGG/Paladog.smali', paladog)

def loading(s):
    def flow(m):
        a = m.index('    :cond_c\n')
        b = m.index('    :cond_12\n', a)
        return m[:a] + '''    :cond_c
    # Both consent screens and their registration requests are removed.
    invoke-static {}, Lcom/common/AppDelegate;->sharedAppDelegate()Lcom/common/AppDelegate;
    move-result-object v1
    iget-object v1, v1, Lcom/common/AppDelegate;->g_GI:Lcom/my/Struct/GAMEINFO;
    iget v3, v1, Lcom/my/Struct/GAMEINFO;->gmGameMode:I
    const/16 v4, 0x1e
    if-lt v3, v4, :cond_12
    const/16 v4, 0x25
    if-gt v3, v4, :cond_12
    const/4 v3, 0x1
    iput v3, v1, Lcom/my/Struct/GAMEINFO;->gmGameMode:I
    goto/16 :goto_0

''' + m[b:]
    return method(s, 'public LoadingProc(F)V', flow)
edit('smali/com/SPC/LoadingLayer.smali', loading)

def library(s):
    for sig in ['public DownloadHtml(Ljava/lang/String;)Ljava/lang/String;', 'public DownloadHtml_Apache(Ljava/lang/String;)Ljava/lang/String;']:
        s = stub(s, sig, '    const/4 v0, 0x0\n    return-object v0')
    for sig in ['public GetServerXML(I)V', 'public HttpFileDownload(Ljava/lang/String;Ljava/lang/String;)V', 'public HttpFileUpload(Ljava/lang/String;Ljava/lang/String;)V']:
        s = stub(s, sig, '    return-void', 0)
    for sig in ['public networkCheck()Z', 'public rootingCheck()Z']:
        s = stub(s, sig, '    const/4 v0, 0x0\n    return v0')
    return s
edit('smali/com/common/Library.smali', library)

def menu(s):
    s = stub(s, 'public BannerModeProc(F)V', '''    invoke-static {}, Lcom/common/AppDelegate;->sharedAppDelegate()Lcom/common/AppDelegate;
    move-result-object v0
    iget-object v0, v0, Lcom/common/AppDelegate;->g_GI:Lcom/my/Struct/GAMEINFO;
    const/4 v1, -0x1
    iput v1, v0, Lcom/my/Struct/GAMEINFO;->iBannerMode:I
    const/4 v1, 0x0
    iput-boolean v1, v0, Lcom/my/Struct/GAMEINFO;->bDownloading:Z
    const-string v1, ""
    iput-object v1, v0, Lcom/my/Struct/GAMEINFO;->szBannerLink:Ljava/lang/String;
    iput-object v1, v0, Lcom/my/Struct/GAMEINFO;->szBannerImgURL:Ljava/lang/String;
    return-void''', 2)
    # Select the existing guarded offline-survival branch; never unlock content.
    def touch(m):
        loc = m.index('    .prologue')
        mapping = '''    move/from16 v0, p1
    const/16 v1, 0x2f
    if-ne v0, v1, :offline_notice_skipped
    return-void
    :offline_notice_skipped
    const/16 v1, 0x83
    if-eq v0, v1, :offline_survival
    const/16 v1, 0xa0
    if-eq v0, v1, :offline_survival
    const/16 v1, 0xa1
    if-ne v0, v1, :offline_original_touch
    :offline_survival
    const/16 v0, 0xa2
    move/16 p1, v0
    :offline_original_touch
'''
        return m[:loc] + mapping + m[loc:]
    s = method(s, 'protected TouchEndUIProc(I)V', touch)
    # Apply after each main menu tick so old animation transitions cannot expose controls.
    s = method(s, 'public MainMenuProc(F)V', lambda m: replace(m, '    return-void', '    invoke-direct/range {p0 .. p0}, Lcom/my/Layer/MainMenuLayer;->HideOnlineControls()V\n    return-void'))
    hidden_ids = [47, 50, 91, 92, 94, 95, 96, 160, 161]
    s += '''
.method private HideOnlineControls()V
    .locals 3
    iget-boolean v0, p0, Lcom/my/Layer/MainMenuLayer;->m_bAfterDeallocForce:Z
    if-nez v0, :offline_ui_return
    invoke-static {}, Lcom/common/AppDelegate;->sharedAppDelegate()Lcom/common/AppDelegate;
    move-result-object v0
    iget-object v0, v0, Lcom/common/AppDelegate;->m_pUIManager:Lcom/my/UI/UIManager;
    if-eqz v0, :offline_ui_return
    const/4 v2, 0x2
'''
    for id_ in hidden_ids:
        s += f'    const/16 v1, 0x{id_:x}\n    invoke-virtual {{v0, v1, v2}}, Lcom/my/UI/UIManager;->ChangeStateByID(II)V\n'
    s += '    :offline_ui_return\n    return-void\n.end method\n'
    return s
edit('smali/com/my/Layer/MainMenuLayer.smali', menu)

def manifest(s):
    ns = 'http://schemas.android.com/apk/res/android'
    ET.register_namespace('android', ns)
    a = '{' + ns + '}'
    root = ET.fromstring(s)
    for node in list(root):
        if node.tag in ['uses-permission', 'permission'] and node.get(a+'name') != 'android.permission.VIBRATE':
            root.remove(node)
    app = root.find('application')
    app.set(a+'allowBackup', 'false')
    app.set(a+'usesCleartextTraffic', 'false')
    for node in list(app):
        name = node.get(a+'name', '')
        if node.tag in ['receiver', 'service'] or name in ['FaceBookActivity', '.ServerSendMessageBoxActivity']:
            app.remove(node)
        elif node.tag == 'activity':
            node.set(a+'exported', 'true' if name == '.Paladog' else 'false')
    ET.indent(root)
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding='unicode') + '\n'
edit('AndroidManifest.xml', manifest)

# Save the exact narrow source delta; game/save serializers and assets are not changed.
diff = ''.join(''.join(difflib.unified_diff((original/p).read_text().splitlines(True), (target/p).read_text().splitlines(True), fromfile='original/'+p, tofile='patched/'+p)) for p in changes)
(base/'evidence/privacy.patch').write_text(diff)
(base/'evidence/changed-files.json').write_text(json.dumps(changes, indent=2))
print(json.dumps({'changed_files': changes, 'target': str(target)}, indent=2))
