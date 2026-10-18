#!/usr/bin/env bash
# Headless Linux graphics test helper; this is not required on a real desktop.
# Prerequisites: Xvfb, xauth, xdpyinfo; check also uses glxinfo.
# Debian packages may instead be extracted (not installed) beneath
# $PALADOG_TEST_ENV/root. This script never downloads packages or uses sudo.
#
#   tools/test-display.sh start
#   tools/test-display.sh check
#   tools/test-display.sh exec ./run.sh
#   tools/test-display.sh trace /tmp/paladog-network.trace ./run.sh
#   tools/test-display.sh exec xdotool search --name Paladog
#   tools/test-display.sh capture /tmp/paladog.png
#   tools/test-display.sh stop
#
# This defaults to software Mesa and OpenAL's silent null backend. Successful
# playback initialization with the null backend is not evidence of audible sound.
set -euo pipefail

test_env="${PALADOG_TEST_ENV:-${XDG_CACHE_HOME:-$HOME/.cache}/paladog-linux/test-env}"
export DISPLAY="${PALADOG_TEST_DISPLAY:-:91}"
if [[ ! "$DISPLAY" =~ ^:[0-9]+$ ]]; then
  echo "PALADOG_TEST_DISPLAY must be a local display such as :91" >&2
  exit 2
fi
display_id="${DISPLAY#:}"
state_dir="$test_env/display-$display_id"
mkdir -p "$state_dir"
chmod 700 "$state_dir"
export XDG_RUNTIME_DIR="$state_dir/runtime"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
export XDG_CACHE_HOME="${XDG_CACHE_HOME:-$test_env/xdg-cache}"
mkdir -p "$XDG_CACHE_HOME"
export XAUTHORITY="$state_dir/Xauthority"
export PATH="$test_env/root/usr/bin:$PATH"
export LD_LIBRARY_PATH="$test_env/root/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
export ALSOFT_DRIVERS="${ALSOFT_DRIVERS:-null}"
export MESA_SHADER_CACHE_DIR="${MESA_SHADER_CACHE_DIR:-$test_env/mesa-shader-cache}"
mkdir -p "$MESA_SHADER_CACHE_DIR"
pid_file="$state_dir/Xvfb.pid"

running() {
  [[ -s "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null &&
    xdpyinfo >/dev/null 2>&1
}

case "${1:-help}" in
  start)
    if running; then
      echo "Xvfb is already available on $DISPLAY"
      exit 0
    fi
    for command in Xvfb xauth xdpyinfo python3; do
      command -v "$command" >/dev/null || {
        echo "Missing prerequisite: $command" >&2
        exit 1
      }
    done
    # Do not take over another process's display or remove its lock/socket.
    if [[ -e "/tmp/.X${display_id}-lock" || -S "/tmp/.X11-unix/X${display_id}" ]]; then
      echo "Display $DISPLAY is in use; choose PALADOG_TEST_DISPLAY=:92" >&2
      exit 1
    fi
    touch "$XAUTHORITY"
    chmod 600 "$XAUTHORITY"
    cookie="$(python3 -c 'import secrets; print(secrets.token_hex(16))')"
    xauth -f "$XAUTHORITY" add "$DISPLAY" MIT-MAGIC-COOKIE-1 "$cookie"
    unset cookie
    nohup Xvfb "$DISPLAY" -screen 0 "${PALADOG_TEST_SCREEN:-1280x720x24}" \
      -nolisten tcp -auth "$XAUTHORITY" -fp built-ins +extension GLX \
      +extension RANDR -noreset >"$state_dir/Xvfb.log" 2>&1 </dev/null &
    echo "$!" >"$pid_file"
    for ((attempt = 0; attempt < 50; attempt++)); do
      if running; then
        echo "Xvfb ready on $DISPLAY; log: $state_dir/Xvfb.log"
        exit 0
      fi
      sleep 0.1
    done
    cat "$state_dir/Xvfb.log" >&2
    echo "Xvfb did not become ready" >&2
    exit 1
    ;;
  stop)
    if [[ -s "$pid_file" ]]; then
      pid="$(cat "$pid_file")"
      # Verify the recorded PID before sending a signal; it may have been reused.
      if [[ -r "/proc/$pid/comm" ]] && [[ "$(cat "/proc/$pid/comm")" == Xvfb ]]; then
        kill "$pid"
      fi
      rm -f "$pid_file"
    fi
    ;;
  check)
    running || { echo "Start the test display first" >&2; exit 1; }
    xdpyinfo | sed -n '1,8p'
    glxinfo -B
    ;;
  exec)
    shift
    [[ $# -gt 0 ]] || { echo "Usage: $0 exec COMMAND [ARG...]" >&2; exit 2; }
    running || { echo "Start the test display first" >&2; exit 1; }
    exec "$@"
    ;;
  trace)
    shift
    [[ $# -ge 2 ]] || { echo "Usage: $0 trace OUTPUT.trace COMMAND [ARG...]" >&2; exit 2; }
    running || { echo "Start the test display first" >&2; exit 1; }
    trace_file="$1"
    shift
    # Trace only the command we launch and its children, never unrelated PIDs.
    # Raw traces can contain payload bytes; keep them in private test evidence.
    umask 077
    exec strace -f -qq -yy -s 160 -e trace=network,execve -o "$trace_file" "$@"
    ;;
  capture)
    [[ $# == 2 ]] || { echo "Usage: $0 capture OUTPUT.png" >&2; exit 2; }
    running || { echo "Start the test display first" >&2; exit 1; }
    import -display "$DISPLAY" -window root "$2"
    ;;
  *)
    echo "Usage: $0 {start|stop|check|exec COMMAND [ARG...]|trace OUTPUT.trace COMMAND [ARG...]|capture OUTPUT.png}"
    ;;
esac
