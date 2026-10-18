#!/usr/bin/env bash
# Test an existing build without rebuilding, downloading, or using player saves.
set -euo pipefail

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export PALADOG_CACHE=${PALADOG_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/paladog-linux}
java_command=${JAVA:-java}
reference_apk=${PALADOG_APK:-$root/../paladog-work/output/Paladog-2.2.0-modern-offline.apk}
if [[ $# != 0 ]]; then
  echo "Usage: PALADOG_CACHE=<cache> DISPLAY=<display> $0" >&2
  echo "See tests/README.md for the headless test-display wrapper." >&2
  exit 2
fi
for required in "$root/build/runtime-classpath.txt" "$root/build/game.jar" \
    "$PALADOG_CACHE/tools/ecj-3.40.0.jar" "$reference_apk" \
    "$root/tests/input/run-tests.sh"; do
  [[ -f "$required" ]] || { echo "Missing test prerequisite: $required. Build first; tests never download or rebuild." >&2; exit 1; }
done
[[ -n ${DISPLAY:-} || -n ${WAYLAND_DISPLAY:-} ]] || {
  echo "Graphics tests require a desktop display. See tests/README.md for Xvfb." >&2
  exit 1
}

report="$root/build/tests"
test_classes="$report/classes"
mkdir -p "$test_classes"
test_data=$(mktemp -d "${TMPDIR:-/tmp}/paladog-suite-data.XXXXXXXX")
trap 'rm -rf -- "$test_data"' EXIT
classpath=$(cat "$root/build/runtime-classpath.txt")
summary="$report/summary.txt"
printf 'Paladog Linux adapter test suite\nUTC: %s\n' "$(date -u +%FT%TZ)" > "$summary"
printf 'Existing build only; temporary test saves; OpenAL loopback output.\n\n' >> "$summary"

snapshot_classes() {
  python3 - "$root/build/classes" <<'PY'
from pathlib import Path
import hashlib, sys
root = Path(sys.argv[1])
for file in sorted(root.rglob("*.class")):
    print(hashlib.sha256(file.read_bytes()).hexdigest(), file.relative_to(root))
PY
}
snapshot_classes > "$report/classes-before.sha256"
failures=0
run_test() {
  local name=$1
  shift
  printf '\n[%s]\n' "$name"
  if "$@" 2>&1 | tee "$report/$name.log"; then
    printf 'PASS %s\n' "$name" >> "$summary"
    return 0
  else
    printf 'FAIL %s\n' "$name" >> "$summary"
    failures=$((failures + 1))
    return 1
  fi
}

if ! run_test compile-tests "$java_command" -jar "$PALADOG_CACHE/tools/ecj-3.40.0.jar" \
    -17 -encoding UTF-8 -nowarn -proc:none -classpath "$classpath" -d "$test_classes" \
    "$root/src/test/java/android/content/StorageTest.java" \
    "$root/src/test/java/android/content/ResourcesTest.java" \
    "$root/src/test/java/android/content/OfflinePlistTest.java" \
    "$root/tests/audio/AudioSelfTest.java" "$root/tests/audio/AudioResourceSelfTest.java"; then
  cat "$summary"
  exit 1
fi
test_java=("$java_command" -Djava.awt.headless=true "-Dpaladog.data=$test_data" \
  "-Dpaladog.assets=$root/build/apk" -cp "$test_classes:$classpath")
run_test storage "${test_java[@]}" android.content.StorageTest || true
run_test resources "${test_java[@]}" android.content.ResourcesTest || true
run_test offline-plist "${test_java[@]}" android.content.OfflinePlistTest || true
run_test audio-codec-loopback "${test_java[@]}" paladog.linux.audio.AudioSelfTest "$reference_apk" || true
run_test audio-resources "${test_java[@]}" paladog.linux.audio.AudioResourceSelfTest "$root/build/apk" || true
run_test rendering bash "$root/tests/render/run-tests.sh" || true
run_test input bash "$root/tests/input/run-tests.sh" || true

snapshot_classes > "$report/classes-after.sha256"
if cmp -s "$report/classes-before.sha256" "$report/classes-after.sha256"; then
  printf 'PASS adapter-classes-unchanged\n' >> "$summary"
else
  printf 'FAIL adapter-classes-changed-during-tests (rerun after builds finish)\n' >> "$summary"
  failures=$((failures + 1))
fi
printf '\nFailed groups: %s\n' "$failures" >> "$summary"
cat "$summary"
echo "Test logs: $report"
[[ $failures == 0 ]]
