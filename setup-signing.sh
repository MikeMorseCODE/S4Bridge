#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
mkdir -p signing-native
if [ -f signing-native/s4bridge.keystore ]; then
    echo "signing-native/s4bridge.keystore already exists."
    exit 0
fi
keytool -genkeypair -v -keystore signing-native/s4bridge.keystore -alias s4bridge -keyalg RSA -keysize 2048 -validity 10000 -storepass s4bridge123 -keypass s4bridge123 -dname "CN=S4Bridge Dev,O=S4Bridge,C=US"
echo "Created signing-native/s4bridge.keystore"
