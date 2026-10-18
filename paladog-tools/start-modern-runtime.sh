#!/usr/bin/env bash
# Separate Android 16 x86_64 guest; never touches the legacy ARM32 AVD.
set -euo pipefail
runtime_dir=/workspace/paladog-tools/runtime
export ANDROID_SDK_ROOT="$runtime_dir"
export ANDROID_HOME="$runtime_dir"
export ANDROID_USER_HOME="$runtime_dir/user36"
export ANDROID_AVD_HOME="$runtime_dir/avd36"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME/paladog_modern_api36.avd" "$runtime_dir/logs"
if [[ ! -e "$ANDROID_AVD_HOME/paladog_modern_api36.ini" ]]; then
cat > "$ANDROID_AVD_HOME/paladog_modern_api36.ini" <<EOF
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/paladog_modern_api36.avd
target=android-36
EOF
fi
if [[ ! -e "$ANDROID_AVD_HOME/paladog_modern_api36.avd/config.ini" ]]; then
cat > "$ANDROID_AVD_HOME/paladog_modern_api36.avd/config.ini" <<'EOF'
AvdId=paladog_modern_api36
avd.ini.displayname=Paladog Android 16 isolated validation
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=720
hw.lcd.height=1560
hw.lcd.density=320
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.keyboard=yes
hw.mainKeys=no
hw.sdCard=no
hw.camera.back=none
hw.camera.front=none
disk.dataPartition.size=2G
image.sysdir.1=system-images/android-36/default/x86_64/
tag.id=default
tag.display=Default
PlayStore.enabled=false
showDeviceFrame=no
EOF
fi
exec "$runtime_dir/emulator/emulator" -avd paladog_modern_api36 \
  -port 5558 -no-window -no-audio -no-boot-anim -no-snapshot -no-metrics \
  -gpu swiftshader -accel off -cores 2 -memory 2048 \
  -camera-back none -camera-front none \
  -http-proxy http://127.0.0.1:9 \
  -network-user-mode-options restrict=on -wifi-user-mode-options restrict=on "$@"
