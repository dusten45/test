#!/usr/bin/env python3
"""Package only the reviewed public allowlist; never traverse the work tree."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import re
import zipfile

WORKSPACE = Path('/workspace')
ROOT = WORKSPACE / 'paladog-work'
README = ROOT / 'output/REPRODUCE.md'
DEFAULT_OUTPUT = ROOT / 'output/Paladog-reproducibility.zip'
ALLOWLIST = (
    'paladog-tools/install.sh',
    'paladog-tools/install-with-runtime.sh',
    'paladog-tools/start-instructions.md',
    'paladog-tools/install-runtime.py',
    'paladog-tools/start-runtime.sh',
    'paladog-tools/lock-runtime-network.sh',
    'paladog-tools/install-modern-runtime.py',
    'paladog-tools/start-modern-runtime.sh',
    'paladog-tools/prepare-modern-runtime.sh',
    'paladog-tools/lock-modern-runtime-network.sh',
    'paladog-tools/runtime/RUNTIME.md',
    'paladog-work/package_repro.py',
    'paladog-work/apply_privacy_patch.py',
    'paladog-work/assemble_minimal_apk.py',
    'paladog-work/verify_static.py',
    'paladog-work/rebuild.sh',
    'paladog-work/sign-build.sh',
    'paladog-work/prepare_modern.py',
    'paladog-work/assemble_modern_apk.py',
    'paladog-work/rebuild-modern.sh',
    'paladog-work/verify_modern.py',
    'paladog-work/output/REPRODUCE.md',
    'paladog-work/output/Paladog-offline-build-notes.md',
    'paladog-work/output/Paladog-modern-build-notes.md',
    'paladog-work/output/Paladog-2.2.0-offline-privacy.apk.sha256',
    'paladog-work/output/Paladog-2.2.0-modern-offline.apk.sha256',
    'paladog-work/evidence/changed-files.json',
    'paladog-work/evidence/privacy.patch',
    'paladog-work/evidence/static-validation.json',
    'paladog-work/evidence/original-signature.txt',
    'paladog-work/evidence/patched-signature.txt',
    'paladog-work/evidence/patch-save-review.md',
    'paladog-work/evidence/control-flow-patch-review.md',
    'paladog-work/evidence/external-paths-review.md',
    'paladog-work/evidence/modern-prepare.json',
    'paladog-work/evidence/modern-static-validation.json',
    'paladog-work/evidence/modern-signature.txt',
    'paladog-work/evidence/modern-native-audit.md',
    'paladog-work/evidence/modern-text-fit-review.md',
    'paladog-work/evidence/android16-compatibility-review.md',
    'paladog-work/evidence/runtime/final-api23/RESULT.md',
    'paladog-work/evidence/runtime/final-api23/03-rankclose-04.png',
    'paladog-work/evidence/runtime/final-api23/10-live-summon.png',
    'paladog-work/evidence/runtime/final-api23/11-exit-dialog.png',
    'paladog-work/evidence/runtime/final-api23/13-relaunch-03.png',
    'paladog-work/evidence/runtime/final-api23/14-resume-06.png',
    'paladog-work/evidence/runtime/final-api23/15-restored-paused.png',
    'paladog-work/evidence/runtime/final-api36/RESULT.md',
    'paladog-work/evidence/runtime/final-api36/01-fresh-title.png',
    'paladog-work/evidence/runtime/final-api36/02-crossarch-load-09.png',
    'paladog-work/evidence/runtime/final-api36/07-summon-and-back.png',
    'paladog-work/evidence/runtime/final-api36/09-native64-relaunch-02.png',
    'paladog-work/evidence/runtime/final-api36/10-native64-resume-04.png',
    'paladog-work/evidence/runtime/final-api36/11-final-quit-dialog.png',
    'paladog-work/evidence/runtime/final-api36-textfix/RESULT.md',
    'paladog-work/evidence/runtime/final-api36-textfix/03-quit-text-fixed.png',
    'paladog-work/evidence/runtime/final-api36-textfix/04-relaunch-02.png',
    'paladog-work/evidence/runtime/final-api36-textfix/07-restored-live-combat.png',
    'paladog-work/evidence/runtime/final-api23-textfix/RESULT.md',
    'paladog-work/evidence/runtime/final-api23-textfix/battle-first.png',
    'paladog-work/evidence/runtime/final-api23-textfix/resume-prompt.png',
    'paladog-work/evidence/runtime/final-api23-textfix/trial2-quit-dialog.png',
)


def checked_input(relative):
    path = PurePosixPath(relative)
    assert not path.is_absolute() and '..' not in path.parts
    assert path.parts[0] in {'paladog-work', 'paladog-tools'}
    assert not set(path.parts) & {'private', 'input', 'files', 'shared_prefs', 'logs'}
    assert not relative.lower().endswith(('.apk', '.idsig', '.p12', '.jks', '.keystore', '.sav'))
    source = WORKSPACE / path
    for candidate in (source, *source.parents):
        assert not candidate.is_symlink(), 'Symlink is not allowed: ' + str(candidate)
        if candidate == WORKSPACE:
            break
    assert source.is_file(), 'Missing allowed file: ' + relative
    data = source.read_bytes()
    if path.suffix == '.png':
        assert data.startswith(b'\x89PNG\r\n\x1a\n'), 'Invalid PNG: ' + relative
    else:
        text = data.decode('utf-8')
        assert not re.search(r'-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----', text), 'Private key content detected'
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true', help='Validate inputs without creating a ZIP')
    mode.add_argument('--create', action='store_true', help='Create a new ZIP after all reports are final')
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    assert len(ALLOWLIST) == len(set(ALLOWLIST)), 'Duplicate allowlist entry'
    section = README.read_text().split('## 공개 재현 자료 ZIP의 명시적 포함 목록', 1)[1]
    block = re.search(r'```text\n(.*?)\n```', section, re.S)
    assert block, 'README allowlist missing'
    documented = tuple(line.strip() for line in block.group(1).splitlines() if line.strip())
    assert documented == ALLOWLIST, 'README and script allowlists differ; update both explicitly'
    # Package only this read snapshot; run after all report edits are complete.
    contents = {relative: checked_input(relative) for relative in ALLOWLIST}
    summary = {'allowed_files': len(contents), 'input_bytes': sum(map(len, contents.values())), 'private_keys_apks_raw_saves_logs_included': False}
    if args.check:
        print(json.dumps({'mode': 'check-only', **summary, 'zip_created': False}, indent=2))
        return
    output = args.output.absolute()
    assert output.parent == ROOT / 'output' and output.suffix == '.zip', 'Output must be a ZIP in the existing output directory'
    assert not output.exists() and not output.is_symlink(), 'Refusing to overwrite existing output'
    for parent in output.parents:
        assert not parent.is_symlink(), 'Refusing symlink output directory'
        if parent == WORKSPACE:
            break
    with zipfile.ZipFile(output, mode='x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for relative, data in contents.items():
            member = zipfile.ZipInfo(relative, date_time=(1980, 1, 1, 0, 0, 0))
            member.compress_type = zipfile.ZIP_DEFLATED
            member.create_system = 3
            mode_bits = 0o755 if relative.endswith(('.sh', '.py')) else 0o644
            member.external_attr = (0o100000 | mode_bits) << 16
            archive.writestr(member, data)
    with zipfile.ZipFile(output) as archive:
        assert archive.namelist() == list(ALLOWLIST)
        assert archive.testzip() is None
        assert all(archive.read(relative) == data for relative, data in contents.items())
    print(json.dumps({'mode': 'created', **summary, 'zip': str(output), 'bytes': output.stat().st_size, 'sha256': hashlib.sha256(output.read_bytes()).hexdigest()}, indent=2))


if __name__ == '__main__':
    main()
