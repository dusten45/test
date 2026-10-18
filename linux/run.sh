#!/usr/bin/env bash
set -euo pipefail
APP_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ $(uname -s) != Linux || $(uname -m) != x86_64 ]]; then
    echo 'This package requires Linux x86_64.' >&2
    exit 1
fi
if [[ -x "$APP_DIR/runtime/bin/java" ]]; then
    JAVA_BIN="$APP_DIR/runtime/bin/java"
    GAME_CLASSPATH="$APP_DIR/lib/adapter.jar:$APP_DIR/lib/game.jar:$APP_DIR/lib/*"
    ASSETS="$APP_DIR/assets"
    FONT="$APP_DIR/fonts/NotoSansCJKkr-Regular.otf"
else
    CACHE=${PALADOG_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/paladog-linux}
    JAVA_BIN=${JAVA:-java}
    if [[ ! -f "$APP_DIR/build/runtime-classpath.txt" ]]; then
        echo 'Run ./build.sh first, or use the complete portable package.' >&2
        exit 1
    fi
    GAME_CLASSPATH=$(cat -- "$APP_DIR/build/runtime-classpath.txt")
    ASSETS="$APP_DIR/build/apk"
    FONT="$CACHE/fonts/NotoSansCJKkr-Regular.otf"
fi
exec "$JAVA_BIN" -Xms128m -Xmx1024m -Dfile.encoding=UTF-8 \
    -Dpaladog.assets="$ASSETS" -Dpaladog.font="$FONT" \
    -cp "$GAME_CLASSPATH" paladog.linux.Main "$@"
