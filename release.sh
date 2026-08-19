#!/bin/bash
# Usage: ./release.sh 1.3.0
# Bumps version, commits, tags, and pushes to trigger GitHub Actions release

VERSION="${1:?Usage: ./release.sh X.Y.Z}"

set -e

echo "=== Releasing v$VERSION ==="

# 1. Bump versions
./bump_version.sh "$VERSION"

# 2. Commit
git add -A
git commit -m "Release v$VERSION

Co-authored-by: Balthor <balthor@agentmail.to>"

# 3. Tag and push
git tag -f "v$VERSION"
git push origin main
git push -f origin "v$VERSION"

echo ""
echo "=== Done! ==="
echo "GitHub Actions will build and publish the APK at:"
echo "https://github.com/croycrabtree/tv-tenderr/releases/tag/v$VERSION"
