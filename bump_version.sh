#!/bin/bash
# Usage: ./bump_version.sh 1.3.0
# Updates Android versionName, versionCode, and web UI version

VERSION="${1:?Usage: ./bump_version.sh X.Y.Z}"
MAJOR=$(echo "$VERSION" | cut -d. -f1)
MINOR=$(echo "$VERSION" | cut -d. -f2)
PATCH=$(echo "$VERSION" | cut -d. -f3)
VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + ${PATCH:-0}))

echo "Bumping to v$VERSION (versionCode=$VERSION_CODE)"

# Update Android build.gradle.kts
sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" android/app/build.gradle.kts
sed -i "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" android/app/build.gradle.kts

# Update web UI version
sed -i "s/Web UI: v[0-9.]*/Web UI: v$VERSION/" web/index.html

echo "Updated:"
echo "  android/app/build.gradle.kts -> versionName=$VERSION, versionCode=$VERSION_CODE"
echo "  web/index.html -> Web UI: v$VERSION"
