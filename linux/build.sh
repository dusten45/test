#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export PALADOG_CACHE=${PALADOG_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/paladog-linux}
export JAVA=${JAVA:-java}
if [[ $# -gt 1 || ($# -eq 1 && "$1" != --offline) ]]; then
    echo "Usage: PALADOG_CACHE=<cache-directory> $0 [--offline]" >&2
    exit 2
fi
python3 "$ROOT/tools/prepare-dependencies.py" "$@"
python3 "$ROOT/tools/convert-game.py" --cache "$PALADOG_CACHE"

python3 - "$ROOT" "$PALADOG_CACHE" <<'PY'
from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys
root, cache = map(lambda value: Path(value).resolve(), sys.argv[1:])
build = root / "build"
classes = build / "classes"
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
dependencies = json.loads((cache / "dependencies.json").read_text())
libraries = sorted(cache / item["path"] for item in dependencies if item["path"].startswith("lib/"))
classpath = os.pathsep.join(map(str, [build / "game.jar", *libraries]))
sources = sorted((root / "src/main/java").rglob("*.java"))
if not sources:
    raise SystemExit("No Linux adapter source files yet")
subprocess.run([os.environ.get("JAVA", "java"), "-jar", str(cache / "tools/ecj-3.40.0.jar"),
    "-17", "-encoding", "UTF-8", "-nowarn", "-proc:none", "-classpath", classpath,
    "-d", str(classes), *map(str, sources)], check=True)
(build / "dependency-classpath.txt").write_text(os.pathsep.join(map(str, libraries)) + "\n")
(build / "runtime-classpath.txt").write_text(str(classes) + os.pathsep + classpath + "\n")
verification_classes = build / "verification-classes"
verification_classes.mkdir(exist_ok=True)
subprocess.run([os.environ.get("JAVA", "java"), "-jar", str(cache / "tools/ecj-3.40.0.jar"),
    "-17", "-encoding", "UTF-8", "-nowarn", "-proc:none", "-d", str(verification_classes),
    str(root / "tools/VerifyClasses.java")], check=True)
verification = subprocess.run([os.environ.get("JAVA", "java"), "-Xverify:all", "-cp",
    os.pathsep.join([str(verification_classes), str(classes), classpath]),
    "VerifyClasses", str(build / "game.jar")], check=True, text=True, capture_output=True)
(build / "bytecode-verification.txt").write_text(verification.stdout + verification.stderr)
print(verification.stdout.strip())
record = json.loads((build / "conversion.json").read_text())
record["dependencies"] = dependencies
record["adapter_sources"] = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
record["java_version"] = subprocess.run([os.environ.get("JAVA", "java"), "-version"], capture_output=True, text=True, check=True).stderr.strip()
record["compiler"] = "Eclipse ECJ 3.40.0; Java 17 classfile target"
(build / "provenance.json").write_text(json.dumps(record, indent=2) + "\n")
print(f"Compiled {len(sources)} Linux adapter source files to {classes}")
print(f"Runtime classpath: {build / 'runtime-classpath.txt'}")
PY
