#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"

echo "===== S4BRIDGE BUILD ====="

if [ ! -f sdk/android-34.jar ]; then
    echo "ERROR: sdk/android-34.jar is missing."
    exit 1
fi
if [ ! -f signing-native/s4bridge.keystore ]; then
    echo "Signing keystore missing. Run ./setup-signing.sh"
    exit 1
fi

rm -rf build/classes build/dex build/apk
mkdir -p build/classes build/dex build/apk

javac -source 8 -target 8 -classpath sdk/android-34.jar -d build/classes \
src/com/s4bridge/app/engine/DeckEngine.java \
src/com/s4bridge/app/engine/MixerEngine.java \
src/com/s4bridge/app/hardware/S4Mk2Mapping.java \
src/com/s4bridge/app/MainActivity.java

dx --dex --output=build/dex/classes.dex build/classes
test -s build/dex/classes.dex

aapt2 link -I sdk/android-34.jar --manifest AndroidManifest.xml -o build/apk/base.apk
jar uf build/apk/base.apk -C build/dex classes.dex
zipalign -f -p 4 build/apk/base.apk build/apk/S4Bridge-aligned.apk
apksigner sign --ks signing-native/s4bridge.keystore --ks-key-alias s4bridge --ks-pass pass:s4bridge123 --key-pass pass:s4bridge123 --out build/apk/S4Bridge.apk build/apk/S4Bridge-aligned.apk
apksigner verify --verbose build/apk/S4Bridge.apk
su -c "cp $PWD/build/apk/S4Bridge.apk /data/local/tmp/S4Bridge.apk"
su -c "chmod 644 /data/local/tmp/S4Bridge.apk"
su -c "pm install -r /data/local/tmp/S4Bridge.apk"
su -c "monkey -p com.s4bridge.app -c android.intent.category.LAUNCHER 1"
echo "S4Bridge installed and launched."
