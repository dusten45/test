#!/usr/bin/env bash
# Run after build.sh. RenderProbe requires a desktop display (or tools/test-display.sh exec).
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cache=${PALADOG_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/paladog-linux}
java_command=${JAVA:-java}
[[ -f "$root/build/dependency-classpath.txt" ]] || { echo 'Run build.sh first' >&2; exit 1; }
classpath="$root/build/classes:$root/build/game.jar:$(cat "$root/build/dependency-classpath.txt")"
test_classes="$root/build/render-test-classes"
mkdir -p "$test_classes"
"$java_command" -jar "$cache/tools/ecj-3.40.0.jar" -17 -encoding UTF-8 -proc:none \
  -classpath "$classpath" -d "$test_classes" "$root/tests/render/BitmapProbe.java" "$root/tests/render/RenderProbe.java" "$root/tests/render/GLResourceThreadProbe.java"
"$java_command" -Djava.awt.headless=true -Dpaladog.font="$cache/fonts/NotoSansCJKkr-Regular.otf" \
  -cp "$test_classes:$classpath" BitmapProbe
"$java_command" -Djava.awt.headless=true -cp "$test_classes:$classpath" GLResourceThreadProbe
"$java_command" -Djava.awt.headless=true -cp "$test_classes:$classpath" RenderProbe
