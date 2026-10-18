#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "Usage: $0 SYNTHETIC_XML_SNAPSHOT NEW_FIXTURE_ROOT [--food-skills]" >&2
    exit 2
fi
CACHE=${PALADOG_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/paladog-linux}
CP=$(cat "$ROOT/build/runtime-classpath.txt")
# Temporary test classes do not replace or shadow the adapter's build classes.
TEST_CLASSES=$(mktemp -d "${TMPDIR:-/tmp}/paladog-control-fixtures.XXXXXXXX")
trap 'rm -rf -- "$TEST_CLASSES"' EXIT
"${JAVA:-java}" -jar "$CACHE/tools/ecj-3.40.0.jar" -17 -encoding UTF-8 -nowarn -proc:none \
    -cp "$CP" -d "$TEST_CLASSES" "$ROOT/tests/gameplay/PrepareControlFixtures.java"
"${JAVA:-java}" -cp "$TEST_CLASSES:$CP" PrepareControlFixtures "$@"
