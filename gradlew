#!/bin/sh

# Standard POSIX runner for Gradle
set -e

# Determine the directory of this script
APP_HOME="$(cd -P -- "$(dirname -- "$0")" && pwd -P)"

# If gradle is available in PATH, use it
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# Fallback: check if Android Studio or local Gradle wrapper is available
echo "Gradle command not found in PATH."
echo "Please install Gradle or run the project in Android Studio."
echo "On GitHub Actions CI, Gradle 8.7 is set up automatically."
exit 1
