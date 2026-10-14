#!/bin/bash
set -e

KEEP_BIN=""
[ "${1:-}" = "--keep-bin" ] && { KEEP_BIN=1; shift; }
VERSION="${1:-$(grep '^version=' module.prop | cut -d'=' -f2)}"
DIST_DIR="dist"
BIN_DIR="tailscale/bin"

echo "Building Magisk-Tailscaled ${VERSION}"
echo "========================================"

mkdir -p "$BIN_DIR" "$DIST_DIR"

# Native binaries are built from source (see tailscaled/), not downloaded.
if [ ! -f "$BIN_DIR/tailscaled-arm64" ] || [ ! -f "$BIN_DIR/tailscaled-arm" ]; then
  ./tailscaled/build.sh
fi
if [ ! -f "$BIN_DIR/jq-arm64" ] || [ ! -f "$BIN_DIR/jq-arm" ]; then
  ./tailscaled/build-jq.sh
fi

# Raw binaries for the lightweight zip, which downloads them during installation.
for f in "$BIN_DIR"/tailscaled-* "$BIN_DIR"/jq-*; do
  cp "$f" "${DIST_DIR}/$(basename "$f")"
done

echo ""
echo "Creating zip without binaries..."
zip -9 -r "${DIST_DIR}/Magisk-Tailscaled-${VERSION}.zip" . -x "*.git*" "dist/*" "*.zip" "*.json" "*.md" "${BIN_DIR}/*" ".shellcheckrc" "android/*" "tailscaled/*" >/dev/null

echo "Creating zip with binaries..."
zip -9 -r "${DIST_DIR}/Magisk-Tailscaled-${VERSION}-full.zip" . -x "*.git*" "dist/*" "*.zip" "*.json" "*.md" ".shellcheckrc" "android/*" "tailscaled/*" >/dev/null

# --keep-bin: leave binaries for the Android app build, which bundles the full module.
[ -n "$KEEP_BIN" ] || rm -f "$BIN_DIR"/*

echo ""
echo "Build completed successfully!"
echo "========================================"
ls -lh "${DIST_DIR}/"
echo "========================================"
echo "Created: ${DIST_DIR}/Magisk-Tailscaled-${VERSION}.zip (no binaries - downloads on install)"
echo "Created: ${DIST_DIR}/Magisk-Tailscaled-${VERSION}-full.zip (includes all binaries)"

