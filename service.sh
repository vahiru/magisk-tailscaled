#!/system/bin/sh
# late_start service: runs in the background after boot on Magisk, KernelSU and APatch.
MODDIR="${0%/*}"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
	sleep 2
done
# Give netd / connectivity a moment to settle after boot.
sleep 5

if [ -x /data/adb/tailscale/scripts/start.sh ]; then
	/data/adb/tailscale/scripts/start.sh boot
else
	sed -i "s|^description=.*|description=[ ✘ /data/adb/tailscale is missing, please reinstall the module ]|" "$MODDIR/module.prop"
fi
