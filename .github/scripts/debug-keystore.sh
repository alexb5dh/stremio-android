#!/usr/bin/env bash
# Signs CI debug builds with a fixed key from the DEBUG_KEYSTORE_BASE64 secret, so newer builds install over older ones.
# The keystore must use the default debug credentials (store/key password "android", alias "androiddebugkey").
#   debug-keystore.sh install           - put the keystore where the Android Gradle plugin looks for it
#   debug-keystore.sh verify <apk-dir>  - fail if any APK in <apk-dir> is not signed with that keystore
set -euo pipefail

keystore="${ANDROID_USER_HOME:-$HOME/.android}/debug.keystore"

if [ -z "${DEBUG_KEYSTORE_BASE64:-}" ]; then
  echo "::notice::DEBUG_KEYSTORE_BASE64 is not set; debug APKs are signed with a random per-run key."
  exit 0
fi

case "${1:-}" in
  install)
    mkdir -p "$(dirname "$keystore")"
    printf '%s' "$DEBUG_KEYSTORE_BASE64" | base64 --decode > "$keystore"
    ;;
  verify)
    expected=$(keytool -exportcert -keystore "$keystore" -storepass android -alias androiddebugkey | sha256sum | cut -d' ' -f1)
    apksigner=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n1)
    for apk in "$2"/*.apk; do
      actual=$("$apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
      if [ "$actual" != "$expected" ]; then
        echo "::error::$apk is signed with $actual, expected the fixed debug key $expected."
        exit 1
      fi
    done
    echo "Debug APKs are signed with the fixed debug key $expected."
    ;;
  *)
    echo "Usage: $0 install | verify <apk-dir>" >&2
    exit 2
    ;;
esac
