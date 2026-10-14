#!/system/bin/sh
# Installer for Magisk (v20.4+), KernelSU (and forks) and APatch.
# Tested targets: Android 8 (API 26) through Android 17 (API 37).

# shellcheck disable=SC2034
SKIPUNZIP=1

TS_DIR="/data/adb/tailscale"
TS_BIN_DIR="$TS_DIR/bin"
TS_SCRIPTS_DIR="$TS_DIR/scripts"

[ -n "${DEBUG:-}" ] && set -x

if [ "$BOOTMODE" != true ]; then
	ui_print "! Please install from the Magisk / KernelSU / APatch app."
	abort "! Installing from recovery is NOT supported."
fi

if [ "$APATCH" = true ]; then
	ROOT_MGR="APatch ${APATCH_VER:-} (${APATCH_VER_CODE:-?})"
elif [ "$KSU" = true ]; then
	ROOT_MGR="KernelSU ${KSU_KERNEL_VER_CODE:-${KSU_VER_CODE:-?}}"
	[ -n "$KSU_VER_CODE" ] && [ "$KSU_VER_CODE" -lt 10670 ] && abort "! Please update KernelSU and the KernelSU Manager."
else
	ROOT_MGR="Magisk ${MAGISK_VER:-} (${MAGISK_VER_CODE:-?})"
fi

API="${API:-$(getprop ro.build.version.sdk)}"
ui_print "- Root manager : $ROOT_MGR"
ui_print "- Android      : $(getprop ro.build.version.release) (API $API)"
ui_print "- Kernel       : $(uname -r)"
ui_print "- Architecture : $ARCH"

if [ "$API" -lt 26 ]; then
	abort "! Android 8.0 (API 26) or newer is required."
elif [ "$API" -gt 37 ]; then
	ui_print "! Android API $API is newer than this module was tested on (37), continuing anyway."
fi

case "$ARCH" in
arm | arm64) ;;
*) abort "! Unsupported architecture: $ARCH (only arm / arm64)." ;;
esac

# Download helper, only used by the lightweight zip that ships without binaries.
fetch() {
	if command -v curl >/dev/null 2>&1; then
		curl -fsSL --connect-timeout 10 -m "${3:-100}" -o "$2" "$1"
	else
		wget --no-check-certificate -T "${3:-100}" -qO "$2" "$1"
	fi
}
gh_download() {
	repo="$1"
	match="$2"
	fetch "https://api.github.com/repos/${repo}/releases/latest" "$TMPDIR/release.json" 15 || {
		ui_print "! Unable to query https://github.com/${repo}/releases"
		return 1
	}
	url="$(grep '"browser_download_url"' "$TMPDIR/release.json" | grep "$match" | head -n 1 |
		sed 's/.*"browser_download_url": *"\([^"]*\)".*/\1/')"
	[ -n "$url" ] || {
		ui_print "! No asset matching '$match' in ${repo}"
		return 1
	}
	FILENAME="$(basename "$url")"
	ui_print "- Downloading $FILENAME..."
	fetch "$url" "$TMPDIR/$FILENAME" 300 || {
		ui_print "! Download failed"
		return 1
	}
}

# Stop the running daemon (any older version) before replacing its binary.
if [ -x "$TS_SCRIPTS_DIR/tailscaled.service" ]; then
	ui_print "- Stopping running tailscaled"
	"$TS_SCRIPTS_DIR/tailscaled.service" stop >/dev/null 2>&1
fi
if [ -f "$TS_DIR/run/watcher.pid" ]; then
	kill -9 "$(cat "$TS_DIR/run/watcher.pid")" 2>/dev/null
fi
# v2.x watcher
for d in /proc/[0-9]*; do
	grep -q "tailscaled.inotify" "$d/cmdline" 2>/dev/null && kill -9 "${d#/proc/}" 2>/dev/null
done

# Keep state (tailscaled.state, certs, ssh, settings.conf, ...); replace only code.
mkdir -p "$TS_DIR"
[ -f "$TS_DIR/tmp/tailscaled.state" ] && [ ! -f "$TS_DIR/tailscaled.state" ] &&
	mv -f "$TS_DIR/tmp/tailscaled.state" "$TS_DIR/tailscaled.state"
rm -rf "$TS_BIN_DIR" "$TS_SCRIPTS_DIR" "$TS_DIR/settings.sh"
mkdir -p "$TS_BIN_DIR" "$TS_SCRIPTS_DIR" "$TS_DIR/run"

ui_print "- Installing binaries"
unzip -qqjo "$ZIPFILE" "tailscale/bin/*-$ARCH" -d "$TS_BIN_DIR" 2>/dev/null
for f in "$TS_BIN_DIR"/*-"$ARCH"; do
	[ -f "$f" ] && mv -f "$f" "${f%-"$ARCH"}"
done

# Lightweight zip: fetch the binaries built for this module (patched tailscaled,
# 16 KB page aligned) from its GitHub release.
RELEASE_REPO="vahiru/magisk-tailscaled"
if [ ! -f "$TS_BIN_DIR/tailscaled" ]; then
	gh_download "$RELEASE_REPO" "/tailscaled-${ARCH}\"" || abort "! Unable to download tailscaled, use the -full.zip instead."
	mv -f "$TMPDIR/$FILENAME" "$TS_BIN_DIR/tailscaled"
fi
if [ ! -f "$TS_BIN_DIR/jq" ]; then
	if gh_download "$RELEASE_REPO" "/jq-${ARCH}\""; then
		mv -f "$TMPDIR/$FILENAME" "$TS_BIN_DIR/jq"
	else
		ui_print "! jq not available, 'tailscaled.service status' will show plain output."
	fi
fi
rm -f "$TS_BIN_DIR/tailscale"
ln -s tailscaled "$TS_BIN_DIR/tailscale"

ui_print "- Installing scripts"
unzip -qqjo "$ZIPFILE" 'tailscale/scripts/*' -d "$TS_SCRIPTS_DIR"
if [ ! -f "$TS_DIR/settings.conf" ]; then
	unzip -qqjo "$ZIPFILE" 'tailscale/settings.conf' -d "$TS_DIR"
fi
# Settings added in later versions, appended to kept settings.conf files.
if ! grep -q '^TS_VPN_COEXIST=' "$TS_DIR/settings.conf" 2>/dev/null; then
	printf '\n# Keep accepted subnet routes on Tailscale while another VPN app is active.\nTS_VPN_COEXIST=1\n' >>"$TS_DIR/settings.conf"
fi

ui_print "- Extracting module files"
unzip -qqo "$ZIPFILE" module.prop service.sh action.sh uninstall.sh -d "$MODPATH"

# Thin wrappers instead of symlinks: they work with Magisk magic mount, KernelSU
# metamodules (overlayfs or magic mount) and APatch, and on SELinux-enforcing
# Android 16/17 builds that refuse to follow /system symlinks into /data.
write_wrapper() {
	cat >"$1" <<EOF
#!/system/bin/sh
exec $2 "\$@"
EOF
	chmod 0755 "$1"
}
mkdir -p "$MODPATH/system/bin"
write_wrapper "$MODPATH/system/bin/tailscale" "$TS_BIN_DIR/tailscale"
write_wrapper "$MODPATH/system/bin/tailscaled" "$TS_BIN_DIR/tailscaled"
write_wrapper "$MODPATH/system/bin/tailscaled.service" "$TS_SCRIPTS_DIR/tailscaled.service"

# KernelSU 1.x/2.x only mounts system/ when a metamodule is installed. Its su (and
# APatch's) always has its own bin dir in PATH, so put the wrappers there as well.
for d in /data/adb/ksu/bin /data/adb/ap/bin; do
	[ -d "$d" ] || continue
	for f in tailscale tailscaled tailscaled.service; do
		if [ ! -e "$d/$f" ] || grep -q "/data/adb/tailscale/" "$d/$f" 2>/dev/null; then
			cp -f "$MODPATH/system/bin/$f" "$d/$f" && chmod 0755 "$d/$f"
		fi
	done
done

ui_print "- Setting permissions"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/bin" 0 2000 0755 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm_recursive "$TS_BIN_DIR" 0 0 0755 0755 u:object_r:system_file:s0
set_perm_recursive "$TS_SCRIPTS_DIR" 0 0 0755 0755 u:object_r:system_file:s0
chmod 0755 "$TS_DIR"
[ -f "$TS_DIR/tailscaled.state" ] && chmod 0600 "$TS_DIR/tailscaled.state"

# Catch wrong-ABI or 16 KB page-size incompatible binaries now, not at boot.
if ! TS_VER="$("$TS_BIN_DIR/tailscaled" --version 2>&1)"; then
	ui_print "! tailscaled failed to run on this device:"
	ui_print "  $(echo "$TS_VER" | head -n 3)"
	abort "! Aborting installation."
fi
ui_print "- tailscaled   : $(echo "$TS_VER" | head -n 1)"
if [ -x "$TS_BIN_DIR/jq" ] && ! "$TS_BIN_DIR/jq" -n 1 >/dev/null 2>&1; then
	ui_print "! jq cannot run on this kernel (page size $(getconf PAGESIZE 2>/dev/null)), status falls back to plain output."
fi

# Leftovers from v2.x
rm -f /data/adb/service.d/tailscaled_service.sh
rm -f /dev/tailscale /dev/tailscaled /dev/tailscaled.service

ui_print "- Starting tailscaled in background"
"$TS_SCRIPTS_DIR/start.sh" postinstall >/dev/null 2>&1 &

ui_print "-----------------------------------------------------------"
if [ ! -f "$TS_DIR/tailscaled.state" ]; then
	ui_print "  First time? From a root shell run:"
	ui_print "    su -c '$TS_BIN_DIR/tailscale login'"
else
	ui_print "  Manage the daemon with:"
fi
ui_print "    su -c '$TS_SCRIPTS_DIR/tailscaled.service status'"
ui_print "  After reboot 'tailscale' and 'tailscaled.service' are in PATH."
ui_print "  Settings: $TS_DIR/settings.conf"
ui_print "-----------------------------------------------------------"
