#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export PALADOG_CACHE=${PALADOG_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/paladog-linux}
if [[ $# -gt 1 || ($# -eq 1 && "$1" != --offline) ]]; then
    echo "Usage: PALADOG_CACHE=<cache-directory> $0 [--offline]" >&2
    exit 2
fi
"$ROOT/build.sh" "$@"
python3 "$ROOT/tools/collect-licenses.py" --cache "$PALADOG_CACHE" "$@"
python3 - "$ROOT" "$PALADOG_CACHE" <<'PY'
from pathlib import Path
import gzip, hashlib, json, os, shutil, sys, tarfile, zipfile
root, cache = map(lambda value: Path(value).resolve(), sys.argv[1:])
dist = root / "dist"
package = dist / "paladog-linux-x86_64"
sources = dist / "paladog-linux-x86_64-sources"
for path in (package, sources):
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
shutil.copytree(cache / "runtime/jdk-21.0.12.1+1-jre", package / "runtime", symlinks=True)
shutil.copytree(cache / "licenses", package / "licenses")
shutil.copytree(root / "build/apk", package / "assets")
shutil.copytree(cache / "fonts", package / "fonts")
(package / "lib").mkdir()
dependencies = json.loads((root / "build/provenance.json").read_text())["dependencies"]
for item in dependencies:
    if item["path"].startswith("lib/") and not Path(item["path"]).name.startswith("lwjgl-stb-"):
        shutil.copyfile(cache / item["path"], package / item["path"])
shutil.copyfile(root / "build/game.jar", package / "lib/game.jar")
with zipfile.ZipFile(package / "lib/adapter.jar", "w", zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
    for path in sorted((root / "build/classes").rglob("*")):
        if path.is_file():
            info = zipfile.ZipInfo(path.relative_to(root / "build/classes").as_posix(), (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            jar.writestr(info, path.read_bytes())
shutil.copyfile(root / "run.sh", package / "paladog")
(package / "paladog").chmod(0o755)
shutil.copyfile(root / "build/provenance.json", package / "BUILD-PROVENANCE.json")
shutil.copyfile(root / "build/bytecode-verification.txt", package / "BYTECODE-VERIFICATION.txt")
if (root / "README.md").is_file():
    shutil.copyfile(root / "README.md", package / "README.md")
if (root / "evidence").is_dir():
    shutil.copytree(root / "evidence", package / "evidence")
(package / "START.txt").write_text("""Paladog Linux x86_64

Extract the entire directory and run ./paladog from a Linux desktop session.
Java is included. No Android environment, installation, or network is needed.
OpenGL 2.1 compatibility support and X11 are required. Wayland sessions need
XWayland. If audio initialization fails, try ./paladog --mute.
Use ./paladog --help for controls and options, including --data-dir PATH.
Save files are separate from this extracted directory: $XDG_DATA_HOME/paladog,
or ~/.local/share/paladog when XDG_DATA_HOME is unset. Keep this directory when
upgrading. --data-dir PATH selects a separate save directory.

See README.md and licenses/THIRD-PARTY.md for tested scope and component notices.
""")
# Corresponding sources stay outside the runnable package, but ship next to it.
(sources / "third-party").mkdir()
for item in json.loads((root / "tools/package-inputs.json").read_text()):
    if item["path"].startswith("sources/"):
        shutil.copyfile(cache / item["path"], sources / "third-party" / Path(item["path"]).name)
shutil.copytree(root / "src", sources / "adapter/src")
shutil.copytree(root / "tools", sources / "adapter/tools", ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
if (root / "tests").is_dir():
    shutil.copytree(root / "tests", sources / "adapter/tests", ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
if (root / "evidence").is_dir():
    shutil.copytree(root / "evidence", sources / "adapter/evidence")
for name in ("build.sh", "run.sh", "package.sh", "test.sh", "README.md"):
    if (root / name).exists():
        shutil.copy2(root / name, sources / "adapter" / name)
shutil.copyfile(cache / "licenses/SOURCE-INPUTS.json", sources / "SOURCE-INPUTS.json")
(sources / "README.md").write_text("""# Paladog Linux corresponding sources

`third-party/` contains the unmodified upstream source archives listed with their
original locations and hashes in `SOURCE-INPUTS.json`. Temurin JDK sources also
cover its included JRE. OpenAL sources match the commit recorded in the shipped
native jar. `adapter/` contains this port's Java adapter and reproducible tools.

The game's APK is not included in this source companion. To rebuild, obtain the
already-patched reference `Paladog-2.2.0-modern-offline.apk` from the project or
your existing copy. Its required SHA-256 is:

`c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`

Place that file at `paladog-work/output/Paladog-2.2.0-modern-offline.apk` beside
the `adapter/` directory, then run `cd adapter && ./build.sh`. The conversion tool
rejects any other APK. Python 3.11+ (3.12+ for package extraction), Bash, a Java
17+ runtime, and initial HTTPS dependency downloads are required for rebuilding.
The game itself needs no build tools or network to run. See adapter/README.md
and the binary package's licenses/ directory for runtime scope and notices.
""")
for tree in (package, sources):
    hashes = []
    for path in sorted(tree.rglob("*")):
        if path.is_file() and not path.is_symlink():
            with path.open("rb") as stream:
                hashes.append(hashlib.file_digest(stream, "sha256").hexdigest() + "  " + path.relative_to(tree).as_posix())
    (tree / "SHA256SUMS").write_text("\n".join(hashes) + "\n")
    archive = dist / (tree.name + ".tar.gz")
    def stable(info):
        info.uid = info.gid = 0
        info.uname = info.gname = ""
        info.mtime = 0
        info.mode = 0o755 if info.isdir() or (info.isfile() and info.mode & 0o111) else 0o644
        return info
    with archive.open("wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=6) as compressed:
        with tarfile.open(fileobj=compressed, mode="w") as tar:
            tar.add(tree, arcname=tree.name, filter=stable)
    print(f"Created {archive} ({archive.stat().st_size:,} bytes)")
with (dist / "SHA256SUMS").open("w") as checksums:
    for path in sorted(dist.glob("paladog-linux-x86_64*.tar.gz")):
        with path.open("rb") as stream:
            checksums.write(hashlib.file_digest(stream, "sha256").hexdigest() + "  " + path.name + "\n")
PY
