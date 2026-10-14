#!/system/bin/sh
# "Action" button in Magisk (v28+), KernelSU and APatch: toggle tailscaled on/off.
SERVICE=/data/adb/tailscale/scripts/tailscaled.service

if [ ! -x "$SERVICE" ]; then
	echo "! /data/adb/tailscale is missing, please reinstall the module."
	exit 1
fi

# shellcheck source=tailscale/scripts/common.sh
. /data/adb/tailscale/scripts/common.sh

if daemon_pid >/dev/null; then
	echo "- Stopping tailscaled..."
	"$SERVICE" stop
else
	echo "- Starting tailscaled..."
	"$SERVICE" start
fi
echo ""
"$SERVICE" status 2>&1
echo ""
echo "- Log: ${TS_RUN_LOG_FILE}"

