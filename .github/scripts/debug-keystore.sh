#!/usr/bin/env bash
# Signs CI debug builds with a fixed key from the DEBUG_KEYSTORE_BASE64 secret, so newer builds install over older ones.
# The keystore must use the default debug credentials. To create one and the secret value:
#   keytool -genkeypair -keystore debug.keystore -storetype PKCS12 -storepass android -keypass android \
#     -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=Android Debug,O=Android,C=US"
#   base64 -w0 debug.keystore
set -euo pipefail

usage() {
  echo "Usage: $0 install           - install the keystore from DEBUG_KEYSTORE_BASE64 for the following steps" >&2
  echo "       $0 verify <apk-dir>  - fail if any APK in <apk-dir> isn't signed with the installed keystore" >&2
  exit 2
}

install_keystore() {
  if [ -z "${DEBUG_KEYSTORE_BASE64:-}" ]; then
    echo "::notice::DEBUG_KEYSTORE_BASE64 is not set; debug APKs are signed with a random per-run key."
    return
  fi
  local android_user_home="$RUNNER_TEMP/android-user-home"
  local keystore="$android_user_home/debug.keystore"
  mkdir -p "$android_user_home"
  printf '%s' "$DEBUG_KEYSTORE_BASE64" | base64 --decode > "$keystore"
  # GitHub runners set XDG_CONFIG_HOME, which AGP 9.2 prefers to ~/.android for the debug keystore
  echo "ANDROID_USER_HOME=$android_user_home" >> "$GITHUB_ENV"
  echo "DEBUG_KEYSTORE=$keystore" >> "$GITHUB_ENV"
}

verify_apks() {
  local apk_dir="$1"
  [ -n "${DEBUG_KEYSTORE:-}" ] || return 0

  local expected apksigner certs actual
  expected=$(keytool -exportcert -keystore "$DEBUG_KEYSTORE" -storepass android -alias androiddebugkey | sha256sum | cut -d' ' -f1)
  apksigner=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n1)
  for apk in "$apk_dir"/*.apk; do
    certs=$("$apksigner" verify --print-certs "$apk")
    actual=$(sed -n 's/^.*Signer.* certificate SHA-256 digest: //p' <<< "$certs" | sort -u)
    if [ "$actual" != "$expected" ]; then
      echo "$certs"
      echo "::error::$apk is signed with '$actual', expected the fixed debug key $expected."
      exit 1
    fi
  done
  echo "Debug APKs are signed with the fixed debug key $expected."
}

case "${1:-}" in
  install) [ "$#" -eq 1 ] || usage; install_keystore ;;
  verify) [ "$#" -eq 2 ] || usage; verify_apks "$2" ;;
  *) usage ;;
esac
