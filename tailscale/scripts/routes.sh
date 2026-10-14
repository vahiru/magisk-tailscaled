#!/system/bin/sh
# Coexistence with other VPN apps (TS_VPN_COEXIST, default on).
#
# Android sends every socket that did not explicitly pick a network, root included,
# into an active VPN (netd "secure VPN" rules, priority 12000-13000+). Two things
# break because of that, and both are fixed here with ip rules placed before all of
# netd's rules (< 10000):
#
# 1. Underlay: tailscaled's own WireGuard / DERP / control traffic carries the bypass
#    mark (0x10000000/0x1e000000 in this build). This fork already sets Android's
#    "protected from VPN" bit on it; the rule below additionally pins it to the
#    physical default network (Wi-Fi / mobile data) ahead of every netd rule, so it
#    also survives VPN lockdown ("block connections without VPN").
# 2. Overlay: every route tailscaled installs in table 52 (tailnet peers, quad-100,
#    accepted subnet routes) gets its own rule, so tailnet traffic never ends up in
#    the other VPN, whatever priorities a given Android version uses.
#
# Only rules at TS_UNDERLAY_PREF and TS_ROUTE_PREF are touched.
#
#   routes.sh loop    keep rules in sync while tailscaled runs (started by tailscaled.service)
#   routes.sh sync    one sync pass
#   routes.sh clear   remove all rules added by this script
#   routes.sh show    print the managed rules
DIR="$(dirname "$(realpath "$0")")"
# shellcheck source=common.sh
. "$DIR/common.sh"

TS_UNDERLAY_PREF=9500
TS_ROUTE_PREF=9510
TS_ROUTE_TABLE=52
TS_ROUTE_INTERVAL=10
TS_BYPASS_MARK=0x10000000/0x1e000000
# Priorities used by earlier versions, cleaned up on clear.
TS_OLD_PREFS="12510"

# Routes tailscaled currently has in table 52 (peers, quad-100, subnet routes),
# without default, throw and similar routes.
wanted_routes() {
	if [ "$1" = 4 ]; then
		ip -4 route show table "$TS_ROUTE_TABLE" 2>/dev/null | awk '
			$1 == "throw" || $1 == "default" || $1 == "unreachable" || $1 == "blackhole" || $1 == "prohibit" { next }
			!/dev tailscale0/ { next }
			$1 == "0.0.0.0/0" { next }
			{ print $1 }'
	else
		ip -6 route show table "$TS_ROUTE_TABLE" 2>/dev/null | awk '
			$1 == "throw" || $1 == "default" || $1 == "unreachable" || $1 == "blackhole" || $1 == "prohibit" { next }
			!/dev tailscale0/ { next }
			tolower($1) ~ /^fe80:/ || $1 == "::/0" { next }
			{ print $1 }'
	fi
}

# Routing table of Android's physical default network: netd's default-network rule
# "from all fwmark 0x0/0xffff iif lo lookup <table>" (no uidrange, not a VPN).
physical_table() {
	ip -"$1" rule show 2>/dev/null | awk '
		/uidrange/ { next }
		/fwmark 0(x0)?\/0xffff iif lo lookup / {
			t = $NF
			if (t ~ /^(tun|ppp|wg|ipsec|utun|tailscale)/) next
			table = t
		}
		END { if (table != "") print table }'
}

current_underlay() {
	ip -"$1" rule show 2>/dev/null | sed -n "s/^${TS_UNDERLAY_PREF}:.* lookup \([^ ]*\).*/\1/p"
}

sync_underlay() {
	fam="$1"
	want=""
	coexist_enabled && want="$(physical_table "$fam")"
	have="$(current_underlay "$fam")"
	[ "$want" = "$have" ] && return 0
	while ip -"$fam" rule del pref "$TS_UNDERLAY_PREF" 2>/dev/null; do :; done
	if [ -n "$want" ] &&
		ip -"$fam" rule add pref "$TS_UNDERLAY_PREF" fwmark "$TS_BYPASS_MARK" lookup "$want" 2>/dev/null; then
		log Info "VPN coexist: IPv$fam tailscaled traffic -> $want"
	elif [ -n "$have" ]; then
		log Info "VPN coexist: IPv$fam underlay rule removed"
	fi
}

current_rules() {
	ip -"$1" rule show 2>/dev/null | sed -n "s/^${TS_ROUTE_PREF}:.* to \([^ ]*\) lookup ${TS_ROUTE_TABLE}.*/\1/p"
}

coexist_enabled() {
	v="$(sed -n 's/^TS_VPN_COEXIST=//p' "$TS_CONF" 2>/dev/null | tr -d '"' | tail -n 1)"
	[ "${v:-1}" != 0 ]
}

sync_family() {
	fam="$1"
	want="$(wanted_routes "$fam")"
	have="$(current_rules "$fam")"
	coexist_enabled || want=""
	for p in $want; do
		echo "$have" | grep -qxF "$p" && continue
		if ip -"$fam" rule add pref "$TS_ROUTE_PREF" to "$p" lookup "$TS_ROUTE_TABLE" 2>/dev/null; then
			log Info "VPN coexist: $p -> tailscale0"
		fi
	done
	for p in $have; do
		echo "$want" | grep -qxF "$p" && continue
		ip -"$fam" rule del pref "$TS_ROUTE_PREF" to "$p" lookup "$TS_ROUTE_TABLE" 2>/dev/null &&
			log Info "VPN coexist: removed $p"
	done
}

sync_all() {
	for fam in 4 6; do
		for pref in $TS_OLD_PREFS; do
			while ip -"$fam" rule del pref "$pref" 2>/dev/null; do :; done
		done
	done
	sync_underlay 4
	sync_underlay 6
	sync_family 4
	sync_family 6
}

clear_all() {
	for fam in 4 6; do
		for pref in "$TS_UNDERLAY_PREF" "$TS_ROUTE_PREF" $TS_OLD_PREFS; do
			while ip -"$fam" rule del pref "$pref" 2>/dev/null; do :; done
		done
	done
}

case "$1" in
loop)
	# Runs until tailscaled exits, then cleans up after itself.
	iso_last=""
	while daemon_pid >/dev/null; do
		sync_all
		# Warn once per change: routing cannot fix Android's VPN ingress isolation.
		iso="$(vpn_isolation)"
		if [ "$iso" != "$iso_last" ]; then
			[ -n "$iso" ] && log Warning "VPN ingress isolation on ${iso#* } (${iso% *} apps): Android drops tailnet replies to them. In the VPN app exclude 100.64.0.0/10 + fd7a:115c:a1e0::/48, allow apps to bypass the VPN, or exclude the app."
			iso_last="$iso"
		fi
		sleep "$TS_ROUTE_INTERVAL"
	done
	clear_all
	;;
sync) sync_all ;;
clear) clear_all ;;
show)
	for fam in 4 6; do
		echo "IPv$fam tailscaled traffic -> $(current_underlay "$fam" | head -n 1)"
		echo "IPv$fam routes kept on Tailscale:"
		current_rules "$fam" | sed 's/^/  /'
	done
	;;
*)
	echo "usage: $0 {loop|sync|clear|show}"
	exit 1
	;;
esac
