#!/bin/sh
# Builds a signed release APK locally: sh tools/build_release.sh
# Output: AnimeVostBR-v<version>.apk in the project root.
#
# Signing keys come from keystore/keystore.properties (see docs/technical.md); CI passes them
# through RELEASE_* environment variables instead. JAVA_HOME must point to JDK 17.
set -e
cd "$(dirname "$0")/.."

./gradlew --console=plain testDebugUnitTest assembleRelease

VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
APK=app/build/outputs/apk/release/app-release.apk
if [ ! -f "$APK" ]; then
  echo "No signed APK: configure keystore/keystore.properties or RELEASE_* variables" >&2
  exit 1
fi
cp "$APK" "AnimeVostBR-v$VERSION.apk"
ls -la "AnimeVostBR-v$VERSION.apk"
