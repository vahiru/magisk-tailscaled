#!/system/bin/sh
# Entry point used by service.sh (boot), customize.sh (postinstall) and action.sh.
#   start.sh boot         start daemon (if enabled) and the enable/disable watcher
#   start.sh postinstall  restart daemon with the freshly installed binary
#   start.sh watch        (internal) watcher loop, polling fallback without busybox
DIR="$(dirname "$(realpath "$0")")"
# shellcheck source=common.sh
. "$DIR/common.sh"

SERVICE="${TS_SCRIPTS_DIR}/tailscaled.service"

stop_watcher() {
	[ -f "$TS_WATCH_PID_FILE" ] || return 0
	wpid="$(cat "$TS_WATCH_PID_FILE" 2>/dev/null)"
	[ -n "$wpid" ] && grep -q -E "inotifyd|start.sh" "/proc/$wpid/cmdline" 2>/dev/null && kill -9 "$wpid" 2>/dev/null
	rm -f "$TS_WATCH_PID_FILE"
}

# Starts/stops tailscaled when the module is toggled in the root manager.
# Magisk, KernelSU and APatch all create a `disable` file in the module dir.
start_watcher() {
	stop_watcher
	[ -d "$TS_MOD_DIR" ] || return 0
	if [ -n "$BUSYBOX" ] && "$BUSYBOX" inotifyd --help 2>&1 | grep -q -i usage; then
		detach "$BUSYBOX" inotifyd "${TS_SCRIPTS_DIR}/tailscaled.inotify" "${TS_MOD_DIR}:nd" </dev/null >/dev/null 2>&1 &
		echo "$!" >"$TS_WATCH_PID_FILE"
		log Info "Watching ${TS_MOD_DIR} with inotifyd (PID $!)."
	else
		detach sh "${TS_SCRIPTS_DIR}/start.sh" watch </dev/null >/dev/null 2>&1 &
		echo "$!" >"$TS_WATCH_PID_FILE"
		log Info "Watching ${TS_MOD_DIR} by polling (PID $!)."
	fi
}

watch_loop() {
	if module_disabled; then prev=off; else prev=on; fi
	while [ -d "$TS_MOD_DIR" ]; do
		sleep 5
		if module_disabled; then cur=off; else cur=on; fi
		[ "$cur" = "$prev" ] && continue
		prev="$cur"
		if [ "$cur" = on ]; then
			log Info "Module enabled, starting tailscaled."
			"$SERVICE" start
		else
			log Info "Module disabled, stopping tailscaled."
			"$SERVICE" stop
		fi
	done
}

boot() {
	trim_log "$TS_RUN_LOG_FILE"
	version="$(sed -n 's/^version=//p' "$TS_MOD_PROP" 2>/dev/null)"
	log Info "Boot: Magisk Tailscaled ${version:-?}, Android $(getprop ro.build.version.release) (API $(getprop ro.build.version.sdk)), kernel $(uname -r), $(root_manager)."
	if module_disabled; then
		log Info "Module is disabled, not starting tailscaled."
		set_description "✘ module disabled"
	elif [ "$TS_AUTOSTART" != 1 ]; then
		log Info "TS_AUTOSTART=0, not starting tailscaled."
		set_description "✘ autostart off"
	else
		"$SERVICE" start
	fi
	start_watcher
}

case "$1" in
boot | "") boot ;;
postinstall)
	"$SERVICE" stop >/dev/null 2>&1
	"$SERVICE" start >/dev/null 2>&1
	start_watcher
	;;
watch) watch_loop ;;
*)
	echo "usage: $0 {boot|postinstall}"
	exit 1
	;;
esac
