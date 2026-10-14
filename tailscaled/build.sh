#!/bin/bash
# Builds tailscaled for Android from anasfanani/tailscale-android-cli with the patches
# in tailscaled/patches/ applied. Output: tailscale/bin/tailscaled-{arm64,arm}.
#
# Requirements: git, Go (version from the fork's go.mod), Android NDK r27c
# (ANDROID_NDK_HOME or NDK_HOME pointing at it; downloaded to ./.ndk otherwise).
#
# Differences from the fork's own release build:
#  - arm64 is linked for 16 KB pages (Android 15+ devices with 16 KB kernels)
#  - no UPX packing (UPX binaries break on 16 KB page kernels)
set -euo pipefail

FORK_REPO="https://github.com/anasfanani/tailscale-android-cli"
FORK_COMMIT="d6a741131b0d5010797bd737ca3bd7c02f8cb511" # v1.98.8 android
NDK_VERSION="r27c"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/tailscaled/.work}"
SRC="$WORK/src"
OUT="$ROOT/tailscale/bin"
REMOVE_TAGS="aws,bird,tap,kube,completion,completion_scripts,wakeonlan,capture,systray,syspolicy,appconnectors,identityfederation,usermetrics,logtail,netlog,linuxdnsfight,syspolicy,tpm"

mkdir -p "$WORK" "$OUT"

NDK="${ANDROID_NDK_HOME:-${NDK_HOME:-}}"
if [ -z "$NDK" ]; then
	NDK="$WORK/android-ndk-$NDK_VERSION"
	if [ ! -d "$NDK" ]; then
		echo "Downloading NDK $NDK_VERSION..."
		curl -fsSL -o "$WORK/ndk.zip" "https://dl.google.com/android/repository/android-ndk-$NDK_VERSION-linux.zip"
		unzip -q "$WORK/ndk.zip" -d "$WORK"
		rm -f "$WORK/ndk.zip"
	fi
fi
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$TOOLCHAIN/aarch64-linux-android21-clang" ] || { echo "NDK toolchain not found in $NDK"; exit 1; }

if [ ! -d "$SRC/.git" ]; then
	git init -q "$SRC"
	git -C "$SRC" remote add origin "$FORK_REPO"
fi
git -C "$SRC" fetch -q --depth 1 origin "$FORK_COMMIT"
git -C "$SRC" checkout -q -f FETCH_HEAD
git -C "$SRC" clean -qfdx
for p in "$ROOT"/tailscaled/patches/*.patch; do
	echo "Applying $(basename "$p")"
	git -C "$SRC" apply "$p"
done

cd "$SRC"
TAGS="$(GOOS='' GOARCH='' go run ./cmd/featuretags --remove "$REMOVE_TAGS" --add cli)"
eval "$(./build_dist.sh shellvars)"
LDFLAGS="-X tailscale.com/version.longStamp=${VERSION_LONG} -X tailscale.com/version.shortStamp=${VERSION_SHORT} -X tailscale.com/version.gitCommitStamp=${VERSION_GIT_HASH} -w -s"

build() { # <arch> <goarch> <clang> <extra ldflags>
	echo "Building tailscaled $VERSION_SHORT for $1..."
	GOOS=android GOARCH="$2" GOARM=7 CGO_ENABLED=1 CC="$TOOLCHAIN/$3" \
		go build -trimpath -tags="$TAGS" -ldflags="$LDFLAGS $4" -o "$OUT/tailscaled-$1" ./cmd/tailscaled
}
build arm64 arm64 aarch64-linux-android21-clang "-extldflags=-Wl,-z,max-page-size=16384"
build arm arm armv7a-linux-androideabi21-clang ""
ls -lh "$OUT"/tailscaled-*
