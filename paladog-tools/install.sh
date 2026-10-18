#!/usr/bin/env bash
set -euo pipefail
# Local APK tooling; keep the selected repository unchanged.
mkdir -p /workspace/paladog-tools /workspace/paladog-work
(
#!/usr/bin/env bash
set -euo pipefail
tool_root=/workspace/paladog-tools
mkdir -p "$tool_root/downloads" "$tool_root/bin" "$tool_root/framework"
command -v java >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null
# These SHA-256 values pin artifacts obtained from the official HTTPS releases.
# They are local integrity pins, not a separate publisher signature.
fetch_checked() {
  local url="$1" dest="$2" expected="$3"
  if [[ -f "$dest" ]]; then
    printf '%s  %s\n' "$expected" "$dest" | sha256sum --check --status || {
      printf 'Checksum mismatch; preserve and investigate: %s\n' "$dest" >&2
      return 1
    }
  else
    local part
    part=$(mktemp "$dest.download.XXXXXX")
    if ! curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' --connect-timeout 20 --max-time 300 "$url" -o "$part"; then
      rm -f "$part"
      return 1
    fi
    if ! printf '%s  %s\n' "$expected" "$part" | sha256sum --check --status; then
      rm -f "$part"
      return 1
    fi
    mv "$part" "$dest"
  fi
}
fetch_checked https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar "$tool_root/downloads/apktool_2.11.1.jar" 56d59c524fc764263ba8d345754d8daf55b1887818b15cd3b594f555d249e2db
fetch_checked https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip "$tool_root/downloads/jadx-1.5.1.zip" 12fd966431903b8e15c36e5007f19343475be7d8f2a55f082e7a929eeabc937e
python3 - <<'PY'
import pathlib, zipfile
root = pathlib.Path('/workspace/paladog-tools')
target = root / 'jadx-1.5.1'
if not target.exists():
    with zipfile.ZipFile(root / 'downloads/jadx-1.5.1.zip') as archive:
        assert archive.testzip() is None, 'JADX archive CRC failure'
        for member in archive.infolist():
            path = pathlib.PurePosixPath(member.filename)
            assert not path.is_absolute() and '..' not in path.parts, 'Unsafe archive path'
        archive.extractall(target)
    (target / 'bin/jadx').chmod(0o755)
    (target / 'bin/jadx-gui').chmod(0o755)

def managed_file(path, contents):
    if path.exists() and path.read_text() != contents:
        raise SystemExit('Refusing to overwrite differing file: ' + str(path))
    path.write_text(contents)
    path.chmod(0o755)

managed_file(root / 'bin/apktool', '#!/usr/bin/env bash\nset -euo pipefail\nexec java -Xmx2g -jar /workspace/paladog-tools/downloads/apktool_2.11.1.jar "$@"\n')
managed_file(root / 'bin/jadx', '#!/usr/bin/env bash\nset -euo pipefail\nexport JADX_CONFIG_DIR="${JADX_CONFIG_DIR:-/workspace/paladog-tools/jadx-config}"\nexport JADX_CACHE_DIR="${JADX_CACHE_DIR:-/workspace/paladog-tools/jadx-cache}"\nmkdir -p "$JADX_CONFIG_DIR" "$JADX_CACHE_DIR"\nexec /workspace/paladog-tools/jadx-1.5.1/bin/jadx "$@"\n')
PY
"$tool_root/bin/apktool" --version
"$tool_root/bin/jadx" --version

)
(
#!/usr/bin/env bash
# Reusable local-only Android build tools installation. No APKs are executed.
set -euo pipefail
umask 022

android_root=/workspace/paladog-tools/android
android_version=35.0.0
android_archive=build-tools_r35_linux.zip
android_sha1=2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc
android_sha256=bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88
android_size=61958799
android_url=https://dl.google.com/android/repository
android_target="$android_root/build-tools/$android_version"

for android_command in curl unzip python3 java; do
  command -v "$android_command" >/dev/null || { echo "Missing prerequisite: $android_command" >&2; exit 1; }
done
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo 'This pinned package requires Linux x86_64.' >&2; exit 1; }
mkdir -p "$android_root/downloads" "$android_root/build-tools" "$android_root/licenses"
android_stage=$(mktemp -d "$android_root/.install.XXXXXX")
trap 'rm -rf -- "$android_stage"' EXIT

# Keep inherited HTTPS_PROXY/HTTP_PROXY and configured certificate trust intact.
# The repository metadata supplies Google's published SHA-1; the SHA-256 below
# additionally pins the same successfully verified archive obtained over TLS.
if [[ ! -f "$android_root/downloads/repository2-1.xml" ]]; then
  curl --fail --location --retry 2 --connect-timeout 20 --max-time 120 \
    --proto '=https' --proto-redir '=https' --silent --show-error \
    "$android_url/repository2-1.xml" -o "$android_stage/repository2-1.xml"
  mv "$android_stage/repository2-1.xml" "$android_root/downloads/repository2-1.xml"
fi
python3 - "$android_root" "$android_version" "$android_archive" "$android_sha1" "$android_size" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root, version, archive, digest, size = sys.argv[1:]
base = pathlib.Path(root)
metadata = ET.parse(base/'downloads/repository2-1.xml').getroot()
package = next(p for p in metadata.findall('remotePackage') if p.get('path') == 'build-tools;' + version)
record = next(a.find('complete') for a in package.findall('archives/archive') if a.findtext('host-os') == 'linux')
assert (record.findtext('url'), record.findtext('checksum'), record.findtext('size')) == (archive, digest, size), 'Official metadata does not match pinned archive'
license_id = package.find('uses-license').get('ref')
license_text = next(x.text for x in metadata.findall('license') if x.get('id') == license_id)
license_path = base/'licenses'/(license_id + '.txt')
if license_path.exists():
    assert license_path.read_text() == license_text, 'Preserving existing, differing license file; investigate before replacing'
else:
    license_path.write_text(license_text)
PY
if [[ ! -f "$android_root/downloads/$android_archive" ]]; then
  curl --fail --location --retry 2 --connect-timeout 20 --max-time 300 \
    --proto '=https' --proto-redir '=https' --silent --show-error \
    "$android_url/$android_archive" -o "$android_stage/$android_archive"
  python3 - "$android_stage/$android_archive" "$android_sha1" "$android_sha256" "$android_size" <<'PY'
import hashlib, pathlib, sys
path, sha1, sha256, size = sys.argv[1:]
data = pathlib.Path(path).read_bytes()
assert len(data) == int(size), 'Archive size mismatch'
assert hashlib.sha1(data).hexdigest() == sha1, 'Official archive checksum mismatch'
assert hashlib.sha256(data).hexdigest() == sha256, 'Pinned archive checksum mismatch'
PY
  mv "$android_stage/$android_archive" "$android_root/downloads/$android_archive"
fi
python3 - "$android_root/downloads/$android_archive" "$android_sha1" "$android_sha256" "$android_size" <<'PY'
import hashlib, pathlib, sys, zipfile
path, sha1, sha256, size = sys.argv[1:]
data = pathlib.Path(path).read_bytes()
assert len(data) == int(size), 'Cached archive size mismatch'
assert hashlib.sha1(data).hexdigest() == sha1, 'Cached archive official checksum mismatch'
assert hashlib.sha256(data).hexdigest() == sha256, 'Cached archive pinned checksum mismatch'
with zipfile.ZipFile(path) as archive:
    for item in archive.infolist():
        member = pathlib.PurePosixPath(item.filename)
        assert not member.is_absolute() and '..' not in member.parts and member.parts[0] == 'android-15', 'Unexpected archive path'
        assert (item.external_attr >> 16) & 0o170000 != 0o120000, 'Unexpected archive symbolic link'
print('Android Build-Tools archive verified against official metadata and pinned SHA-256.')
PY
if [[ -e "$android_target" ]]; then
  # Do not overwrite existing user files or silently reuse a damaged install.
  python3 - "$android_root/downloads/$android_archive" "$android_target" <<'PY'
import pathlib, sys, zipfile
archive, target = sys.argv[1:]
with zipfile.ZipFile(archive) as package:
    for item in package.infolist():
        if not item.is_dir():
            dest = pathlib.Path(target)/pathlib.PurePosixPath(item.filename).relative_to('android-15')
            assert dest.is_file() and dest.read_bytes() == package.read(item), 'Existing file differs: ' + str(dest)
print('Existing build tools match the verified archive; preserved.')
PY
else
  unzip -q "$android_root/downloads/$android_archive" -d "$android_stage"
  mv "$android_stage/android-15" "$android_target"
fi
"$android_target/apksigner" version
"$android_target/aapt" version
"$android_target/aapt2" version
python3 - "$android_stage/zipalign-smoke.zip" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'w', compression=zipfile.ZIP_STORED) as archive:
    archive.writestr('smoke.txt', 'Android build tools alignment validation\n')
PY
"$android_target/zipalign" 4 "$android_stage/zipalign-smoke.zip" "$android_stage/zipalign-smoke-aligned.zip"
"$android_target/zipalign" -c 4 "$android_stage/zipalign-smoke-aligned.zip"
printf 'zipalign smoke test passed.\n'
printf 'Android build tools installed at %s\n' "$android_target"

)
tool_smoke_file=$(mktemp /workspace/paladog-tools/smoke-install.XXXXXX)
trap 'rm -f "$tool_smoke_file"' EXIT
cat > "$tool_smoke_file" <<'PALADOG_TOOLCHAIN_SMOKE_20261018'
#!/usr/bin/env bash
# A synthetic package checks the toolchain; this is not the Paladog APK.
set -euo pipefail
umask 077
tools_root=/workspace/paladog-tools
android_tools="$tools_root/android/build-tools/35.0.0"
smoke_dir=$(mktemp -d /workspace/paladog-work/toolcheck.XXXXXX)
trap 'rm -f "$smoke_dir/signing-password" "$smoke_dir/test-only.p12"' EXIT
mkdir -p "$smoke_dir/project/smali/org/example/toolcheck" "$smoke_dir/framework"
cat > "$smoke_dir/project/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example.toolcheck" android:versionCode="1" android:versionName="1.0">
  <uses-sdk android:minSdkVersion="7" android:targetSdkVersion="19" />
  <application android:label="Synthetic tool check" android:hasCode="true" />
</manifest>
XML
cat > "$smoke_dir/project/apktool.yml" <<'YAML'
version: 2.11.1
apkFileName: synthetic-toolcheck.apk
isFrameworkApk: false
usesFramework:
  ids:
  - 1
sdkInfo:
  minSdkVersion: 7
  targetSdkVersion: 19
packageInfo:
  forcedPackageId: 127
versionInfo:
  versionCode: 1
  versionName: 1.0
YAML
cat > "$smoke_dir/project/smali/org/example/toolcheck/Probe.smali" <<'SMALI'
.class public Lorg/example/toolcheck/Probe;
.super Ljava/lang/Object;
.method public static message()Ljava/lang/String;
    .registers 1
    const-string v0, "toolkit-verified"
    return-object v0
.end method
SMALI
"$tools_root/bin/apktool" b -p "$smoke_dir/framework" "$smoke_dir/project" -o "$smoke_dir/unsigned.apk"
"$android_tools/zipalign" -p 4 "$smoke_dir/unsigned.apk" "$smoke_dir/aligned.apk"
python3 - "$smoke_dir/signing-password" <<'PY'
import pathlib, secrets, sys
pathlib.Path(sys.argv[1]).write_text(secrets.token_hex(24))
PY
keytool -genkeypair -keystore "$smoke_dir/test-only.p12" -storetype PKCS12 -alias synthetic-test-only -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=Synthetic Toolchain Check' -storepass:file "$smoke_dir/signing-password" -keypass:file "$smoke_dir/signing-password" -noprompt
"$android_tools/apksigner" sign --ks "$smoke_dir/test-only.p12" --ks-key-alias synthetic-test-only --ks-pass "file:$smoke_dir/signing-password" --min-sdk-version 7 --out "$smoke_dir/signed-synthetic.apk" "$smoke_dir/aligned.apk"
"$android_tools/apksigner" verify --verbose --print-certs "$smoke_dir/signed-synthetic.apk"
"$android_tools/zipalign" -c -p 4 "$smoke_dir/signed-synthetic.apk"
"$tools_root/bin/apktool" d -p "$smoke_dir/framework" "$smoke_dir/signed-synthetic.apk" -o "$smoke_dir/decoded"
"$tools_root/bin/jadx" --threads-count 2 --no-res -d "$smoke_dir/java" "$smoke_dir/signed-synthetic.apk"
python3 - "$smoke_dir" <<'PY'
import pathlib, sys, zipfile, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(root/'signed-synthetic.apk') as archive:
    assert archive.testzip() is None
    assert 'classes.dex' in archive.namelist()
assert 'toolkit-verified' in (root/'decoded/smali/org/example/toolcheck/Probe.smali').read_text()
assert 'toolkit-verified' in (root/'java/sources/org/example/toolcheck/Probe.java').read_text()
manifest = ET.parse(root/'decoded/AndroidManifest.xml').getroot()
assert manifest.attrib['package'] == 'org.example.toolcheck'
assert not manifest.findall('uses-permission')
print('PASS: synthetic Smali build, alignment, APK signing/verification, decode, Java decompile and content checks.')
print('Artifacts:', root)
print('This synthetic toolchain check does not run Paladog or Android runtime validation; see the separate final APK reports.')
PY
PALADOG_TOOLCHAIN_SMOKE_20261018
if [[ -f /workspace/paladog-tools/smoke-test.sh ]] && ! cmp -s "$tool_smoke_file" /workspace/paladog-tools/smoke-test.sh; then
  echo "Preserving differing smoke-test.sh; investigate before replacing." >&2
  exit 1
fi
cp "$tool_smoke_file" /workspace/paladog-tools/smoke-test.sh
bash /workspace/paladog-tools/smoke-test.sh
