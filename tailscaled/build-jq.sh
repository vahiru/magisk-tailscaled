#!/bin/bash
# Builds a static-libjq jq for Android (arm64, arm) with the NDK, 16 KB page aligned.
# Output: tailscale/bin/jq-{arm64,arm}. Needs git, autoconf, automake, libtool, make, bison.
set -euo pipefail

JQ_TAG="jq-1.8.1"
NDK_VERSION="r27c"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/tailscaled/.work}"
OUT="$ROOT/tailscale/bin"
mkdir -p "$WORK" "$OUT"

NDK="${ANDROID_NDK_HOME:-${NDK_HOME:-$WORK/android-ndk-$NDK_VERSION}}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$TOOLCHAIN/aarch64-linux-android21-clang" ] || { echo "NDK toolchain not found in $NDK (run tailscaled/build.sh first or set ANDROID_NDK_HOME)"; exit 1; }
export PATH="$TOOLCHAIN:$PATH"

[ -d "$WORK/jq" ] || git clone -q --depth 1 --branch "$JQ_TAG" https://github.com/jqlang/jq "$WORK/jq"
(cd "$WORK/jq" && [ -f configure ] || autoreconf -i >/dev/null)

build() { # <arch> <clang prefix> <host>
	local dir="$WORK/jq-build-$1"
	rm -rf "$dir" && cp -r "$WORK/jq" "$dir"
	(
		cd "$dir"
		CC="$2-clang" AR=llvm-ar RANLIB=llvm-ranlib STRIP=llvm-strip CFLAGS="-O2" \
			LDFLAGS="-Wl,-z,max-page-size=16384" \
			./configure --host="$3" --disable-shared --enable-static --without-oniguruma \
			--disable-docs --disable-valgrind >/dev/null
		make src/builtin.inc src/version.h >/dev/null
		make -j"$(nproc)" jq >/dev/null
		llvm-strip jq
	)
	cp "$dir/jq" "$OUT/jq-$1"
	echo "Built jq for $1"
}
build arm64 aarch64-linux-android21 aarch64-linux-android
build arm armv7a-linux-androideabi21 arm-linux-androideabi
