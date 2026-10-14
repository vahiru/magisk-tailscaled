#!/system/bin/sh
# shellcheck disable=SC2034 # variables are consumed by the scripts that source this file
# Shared environment and helpers for every Magisk Tailscaled script.
# Works under Magisk, KernelSU (and forks such as KernelSU Next / SukiSU) and APatch.
# Only relies on toybox/mksh features that ship with Android 8 through Android 17;
# busybox is used opportunistically, never required.

MODID="magisk-tailscaled"
TS_DIR="/data/adb/tailscale"
TS_BIN_DIR="${TS_DIR}/bin"
TS_SCRIPTS_DIR="${TS_DIR}/scripts"
TS_RUN_DIR="${TS_DIR}/run"
TS_CONF="${TS_DIR}/settings.conf"
TS_MOD_DIR="/data/adb/modules/${MODID}"
TS_MOD_PROP="${TS_MOD_DIR}/module.prop"

TS_DAEMON_BIN="${TS_BIN_DIR}/tailscaled"
TS_CLI_BIN="${TS_BIN_DIR}/tailscale"
TS_LOG_FILE="${TS_RUN_DIR}/tailscaled.log"
TS_PID_FILE="${TS_RUN_DIR}/tailscaled.pid"
TS_WATCH_PID_FILE="${TS_RUN_DIR}/watcher.pid"
TS_RUN_LOG_FILE="${TS_RUN_DIR}/service.log"

# Defaults, may be overridden in settings.conf
TS_DAEMON_ARGS="-no-logs-no-support"
TS_AUTOSTART=1
TS_STOP_TIMEOUT=10
TS_LOG_MAX_KB=2048

# shellcheck source=../settings.conf
[ -f "$TS_CONF" ] && . "$TS_CONF"

export HOME="${TS_DIR}" # tailscaled writes some files to $HOME
export PATH="${TS_BIN_DIR}:${TS_SCRIPTS_DIR}:/system/bin:/system/xbin:/vendor/bin:${PATH}"

mkdir -p "$TS_RUN_DIR" 2>/dev/null

# Root manager detection, used only for informational output.
root_manager() {
	if [ -d /data/adb/ap ] && [ -x /data/adb/ap/bin/busybox ]; then
		echo "APatch"
	elif [ -d /data/adb/ksu ]; then
		echo "KernelSU"
	elif [ -d /data/adb/magisk ]; then
		echo "Magisk"
	else
		echo "unknown"
	fi
}

# Busybox is optional. Every manager ships its own copy in a different place.
find_busybox() {
	for bb in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox; do
		[ -x "$bb" ] && {
			echo "$bb"
			return 0
		}
	done
	command -v busybox 2>/dev/null
}
BUSYBOX="$(find_busybox)"

if [ -t 1 ]; then
	normal="\033[0m"
	orange="\033[1;38;5;208m"
	red="\033[1;31m"
	green="\033[1;32m"
	yellow="\033[1;33m"
	blue="\033[1;34m"
else
	normal="" orange="" red="" green="" yellow="" blue=""
fi

now() { date '+%Y-%m-%d %H:%M:%S'; }

# log <Level> <message>
log() {
	case "$1" in
	Info) color="$blue" ;;
	Success) color="$green" ;;
	Error) color="$red" ;;
	Warning) color="$yellow" ;;
	*) color="$orange" ;;
	esac
	line="$(now) [$1]: $2"
	echo "$line" >>"$TS_RUN_LOG_FILE" 2>/dev/null
	[ -t 1 ] && printf "%b%s%b\n" "$color" "$line" "$normal"
	return 0
}

# Keep a single log file from growing without bound.
trim_log() {
	f="$1"
	[ -f "$f" ] || return 0
	size_kb=$(($(wc -c <"$f") / 1024))
	if [ "$size_kb" -gt "$TS_LOG_MAX_KB" ]; then
		tail -n 2000 "$f" >"$f.tmp" 2>/dev/null && mv -f "$f.tmp" "$f"
	fi
}

# The daemon is identified by its process name / argv[0] being "tailscaled". The
# `tailscale` CLI is the same multicall binary, so /proc/<pid>/exe is not enough.
is_daemon_pid() {
	[ -n "$1" ] && [ -r "/proc/$1/cmdline" ] || return 1
	[ "$(cat "/proc/$1/comm" 2>/dev/null)" = "tailscaled" ] && return 0
	argv0="$(tr '\0' '\n' <"/proc/$1/cmdline" 2>/dev/null | head -n 1)"
	case "$argv0" in
	tailscaled | */tailscaled) return 0 ;;
	esac
	return 1
}

daemon_pid() {
	if [ -f "$TS_PID_FILE" ]; then
		pid="$(cat "$TS_PID_FILE" 2>/dev/null)"
		is_daemon_pid "$pid" && {
			echo "$pid"
			return 0
		}
	fi
	# Fallback: scan /proc (covers daemons started by older module versions).
	for d in /proc/[0-9]*; do
		pid="${d#/proc/}"
		is_daemon_pid "$pid" && {
			echo "$pid"
			return 0
		}
	done
	return 1
}

# Run a command detached from the caller's session (use with `&`), so it survives
# the installer, action button or su shell exiting. exec keeps $! pointing at it.
detach() {
	if command -v setsid >/dev/null 2>&1; then
		exec setsid "$@"
	fi
	exec nohup "$@"
}

module_disabled() {
	[ -f "${TS_MOD_DIR}/disable" ] || [ -f "${TS_MOD_DIR}/remove" ]
}

# Shows the daemon state in the root manager's module list.
set_description() {
	[ -f "$TS_MOD_PROP" ] || return 0
	sed -i "s|^description=.*|description=[ $(date '+%H:%M') $1 ] Tailscale for rooted Android (Magisk / KernelSU / APatch).|" "$TS_MOD_PROP" 2>/dev/null
	return 0
}

[ -n "${DEBUG:-}" ] && {
	PS4='+ ${0##*/}:${LINENO}: '
	set -x
}
true
