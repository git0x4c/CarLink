#!/bin/sh
# Build/test the Legacy (Android 4.4/5.x) product. The main app is unaffected.
set -e
cd "$(dirname "$0")/.."
case "${1:-debug}" in
  test)
    sh ./gradlew :legacy-shared:testDebugUnitTest :legacy-common:testDebugUnitTest \
      -Pdiplay.gl19Diagnostics=true --offline
    ;;
  debug)
    sh ./gradlew :legacy-mobile:assembleDebug -Pdiplay.gl19Diagnostics=true --offline
    ;;
  release)
    : "${DIPLAY_AUTH_ASSETS_DIR:?set DIPLAY_AUTH_ASSETS_DIR to the folder that contains offline-mfi/}"
    sh ./gradlew :legacy-mobile:assembleStandaloneRelease \
      -Pdiplay.gl19Diagnostics=true -Pdiplay.gl19AudioTest=aopus -Pdiplay.gl19Release=true
    ;;
  *)
    echo "usage: $0 {test|debug|release}" >&2
    exit 2
    ;;
esac
