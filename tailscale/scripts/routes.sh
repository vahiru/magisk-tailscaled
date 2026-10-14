#!/system/bin/sh
# VPN coexistence for subnet routes.
#
# tailscaled sends tailnet addresses (100.64.0.0/10, fd7a:115c:a1e0::/48) to table 52
# with an ip rule at priority 12500, ahead of Android's VPN rules (13000). Accepted
# subnet routes (e.g. a home LAN behind a subnet router) are only looked up at 13001,
# so while another VPN app is active they end up inside that VPN instead.
#
# This mirrors every subnet route in table 52 into its own rule at priority 12510 so
# those subnets keep going through Tailscale. Only rules at TS_ROUTE_PREF are touched.
#
#   routes.sh loop    keep rules in sync while tailscaled runs (started by tailscaled.service)
#   routes.sh sync    one sync pass
#   routes.sh clear   remove all rules added by this script
#   routes.sh show    print the managed rules
DIR="$(dirname "$(realpath "$0")")"
# shellcheck source=common.sh
. "$DIR/common.sh"

TS_ROUTE_PREF=12510
TS_ROUTE_TABLE=52
TS_ROUTE_INTERVAL=10

# Subnet routes currently installed by tailscaled in table 52, minus the tailnet
# ranges themselves (already handled by tailscaled), default routes and throw routes.
wanted_routes() {
	if [ "$1" = 4 ]; then
		ip -4 route show table "$TS_ROUTE_TABLE" 2>/dev/null | awk '
			$1 == "throw" || $1 == "default" || $1 == "unreachable" || $1 == "blackhole" { next }
			!/dev tailscale0/ { next }
			{
				split($1, a, /[.\/]/)
				if (a[1] == 100 && a[2] >= 64 && a[2] <= 127) next
				if ($1 == "0.0.0.0/0") next
				print $1
			}'
	else
		ip -6 route show table "$TS_ROUTE_TABLE" 2>/dev/null | awk '
			$1 == "throw" || $1 == "default" || $1 == "unreachable" || $1 == "blackhole" { next }
			!/dev tailscale0/ { next }
			tolower($1) ~ /^fd7a:115c:a1e0:/ || tolower($1) ~ /^fe80:/ || $1 == "::/0" { next }
			{ print $1 }'
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
			log Info "VPN coexist: $p -> table $TS_ROUTE_TABLE (pref $TS_ROUTE_PREF)"
		fi
	done
	for p in $have; do
		echo "$want" | grep -qxF "$p" && continue
		ip -"$fam" rule del pref "$TS_ROUTE_PREF" to "$p" lookup "$TS_ROUTE_TABLE" 2>/dev/null &&
			log Info "VPN coexist: removed $p"
	done
}

sync_all() {
	sync_family 4
	sync_family 6
}

clear_all() {
	for fam in 4 6; do
		while ip -"$fam" rule del pref "$TS_ROUTE_PREF" 2>/dev/null; do :; done
	done
}

case "$1" in
loop)
	# Runs until tailscaled exits, then cleans up after itself.
	while daemon_pid >/dev/null; do
		sync_all
		sleep "$TS_ROUTE_INTERVAL"
	done
	clear_all
	;;
sync) sync_all ;;
clear) clear_all ;;
show)
	echo "IPv4:"
	current_rules 4 | sed 's/^/  /'
	echo "IPv6:"
	current_rules 6 | sed 's/^/  /'
	;;
*)
	echo "usage: $0 {loop|sync|clear|show}"
	exit 1
	;;
esac
