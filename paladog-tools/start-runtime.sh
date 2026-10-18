#!/usr/bin/env bash
set -euo pipefail
runtime_dir=/workspace/paladog-tools/runtime
export ANDROID_SDK_ROOT="$runtime_dir"
export ANDROID_HOME="$runtime_dir"
export ANDROID_USER_HOME="$runtime_dir/user"
export ANDROID_AVD_HOME="$runtime_dir/avd"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME/paladog_offline_api23.avd" "$runtime_dir/logs"
if [[ ! -e "$ANDROID_AVD_HOME/paladog_offline_api23.ini" ]]; then
cat > "$ANDROID_AVD_HOME/paladog_offline_api23.ini" <<EOF
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/paladog_offline_api23.avd
target=android-23
EOF
fi
if [[ ! -e "$ANDROID_AVD_HOME/paladog_offline_api23.avd/config.ini" ]]; then
cat > "$ANDROID_AVD_HOME/paladog_offline_api23.avd/config.ini" <<'EOF'
AvdId=paladog_offline_api23
avd.ini.displayname=Paladog isolated ARM32 validation
abi.type=armeabi-v7a
hw.cpu.arch=arm
hw.cpu.ncore=2
hw.ramSize=1536
hw.lcd.width=800
hw.lcd.height=480
hw.lcd.density=160
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.keyboard=yes
hw.sdCard=no
disk.dataPartition.size=2G
image.sysdir.1=system-images/android-23/default/armeabi-v7a/
tag.id=default
tag.display=Default
PlayStore.enabled=false
showDeviceFrame=no
EOF
fi
exec "$runtime_dir/tools/emulator" -avd paladog_offline_api23 -engine classic \
  -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader -accel off -memory 1536 \
  -http-proxy http://127.0.0.1:9 "$@"
