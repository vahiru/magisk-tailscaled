#!/system/bin/sh
TS_DIR=/data/adb/tailscale

if [ -x "$TS_DIR/scripts/tailscaled.service" ]; then
	"$TS_DIR/scripts/tailscaled.service" stop >/dev/null 2>&1
fi
if [ -f "$TS_DIR/run/watcher.pid" ]; then
	kill -9 "$(cat "$TS_DIR/run/watcher.pid")" 2>/dev/null
fi

rm -rf "$TS_DIR"

# Command wrappers placed in the KernelSU / APatch su PATH.
for d in /data/adb/ksu/bin /data/adb/ap/bin; do
	for f in tailscale tailscaled tailscaled.service; do
		grep -q "/data/adb/tailscale/" "$d/$f" 2>/dev/null && rm -f "$d/$f"
	done
done

# Leftovers from v2.x
rm -f /data/adb/service.d/tailscaled_service.sh
