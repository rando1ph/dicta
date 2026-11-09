#!/usr/bin/env bash
# Explicit per-command toolchain: never changes the user's global Java or SDK.
set -euo pipefail
dicta_repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
export JAVA_HOME="${DICTA_JAVA_HOME:-${JAVA_HOME:-$HOME/.local/share/oraculum-tools/jdk-21}}"
export ANDROID_HOME="${DICTA_ANDROID_HOME:-${ANDROID_HOME:-$HOME/.local/share/dicta-tools/android-sdk}}"
export GRADLE_USER_HOME="${DICTA_GRADLE_USER_HOME:-$HOME/.local/share/dicta-tools/gradle-cache}"
if [[ ! -x "$JAVA_HOME/bin/java" || ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]]; then
    echo 'Set DICTA_JAVA_HOME to JDK 17/21 and DICTA_ANDROID_HOME to an SDK with platform 36 and build-tools 35.0.0.' >&2
    exit 1
fi
cd "$dicta_repo"
if (( $# == 0 )); then
    set -- :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
fi
exec ./gradlew --console=plain "$@"
