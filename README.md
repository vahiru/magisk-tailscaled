[![anasfanani - Magisk-Tailscaled](https://img.shields.io/static/v1?label=anasfanani&message=Magisk-Tailscaled&color=blue&logo=github)](https://github.com/anasfanani/Magisk-Tailscaled "Go to GitHub repo")
[![Check and Update Tailscale Binary](https://github.com/anasfanani/Magisk-Tailscaled/actions/workflows/update.yml/badge.svg)](https://github.com/anasfanani/Magisk-Tailscaled/actions/workflows/update.yml)
[![Github All Releases](https://img.shields.io/github/downloads/anasfanani/Magisk-Tailscaled/total.svg)]()
[![GitHub release](https://img.shields.io/github/release/anasfanani/Magisk-Tailscaled?include_prereleases=&sort=semver&color=blue)](https://github.com/anasfanani/Magisk-Tailscaled/releases/)
[![issues - Magisk-Tailscaled](https://img.shields.io/github/issues/anasfanani/Magisk-Tailscaled)](https://github.com/anasfanani/Magisk-Tailscaled/issues)
[![Static Badge](https://img.shields.io/badge/Discussion-Telegram-blue?style=flat&logo=telegram&link=t.me%2Fsystembinsh%2F158)](https://t.me/systembinsh/158)

# Magisk Tailscaled

This repository contains a Magisk module for running Tailscale on rooted Android devices.

## What is Tailscale?

Tailscale is a networking tool that allows you to connect each of your devices as if they were on the same VPN. For example, an Android phone connected to the Tailscale network can communicate with any other device connected to Tailscale. You can install it on your PC and Android device and then connect them using the Tailscale IP. For more information, check out [How Tailscale Works](https://tailscale.com/blog/how-tailscale-works).

## Difference between this Magisk module and the Tailscale app on Play Store

The [Tailscale app](https://play.google.com/store/apps/details?id=com.tailscale.ipn) on the Play Store runs with Android's VPN, which means you can't use Tailscale while another VPN is active. This Magisk module, on the other hand, allows you to use both an Android VPN and Tailscale at the same time.

## Compatibility

| | Supported |
|---|---|
| Android | 8.0 (API 26) – 17 (API 37), including Android 16 / 17 |
| Root | Magisk v20.4+, KernelSU (incl. KernelSU Next / SukiSU), APatch |
| Kernel | GKI and non-GKI; 4 KB and 16 KB page size (checked at install, `jq` is optional) |
| ABI | `arm64-v8a`, `armeabi-v7a` |

Notes for KernelSU:

- KernelSU 1.x/2.x only mounts a module's `system/` when a *metamodule* is installed.
  This module does not depend on it: the installer also puts `tailscale`, `tailscaled`
  and `tailscaled.service` wrappers into `/data/adb/ksu/bin` (or `/data/adb/ap/bin` on
  APatch), which is always in the `su` PATH.
- Enabling / disabling the module in the manager starts / stops `tailscaled` immediately,
  no reboot needed.
- The manager's **Action** button toggles the daemon and prints its status.

## Quick Start & Installation

1. Download the latest zip from the [Releases](https://github.com/vahiru/magisk-tailscaled/releases/latest) page
   (`-full.zip` ships the binaries, the small zip downloads them during install).
2. Install it from Magisk / KernelSU / APatch. `tailscaled` starts right away, no reboot needed.
3. Log in from a root shell:
   ```sh
   su -c tailscale login
   ```
4. Open the printed URL to authorize the device, then check with `su -c tailscaled.service status`.

If `tailscale` is not found before the first reboot, use the full path:
`su -c /data/adb/tailscale/bin/tailscale login`.

## Using it together with another VPN app

tailscaled uses its own `tailscale0` interface plus policy routing rules placed around
Android's VPN rules (priority 13000), so it runs side by side with VPN apps:

| Traffic | With another VPN active |
|---|---|
| Tailnet devices (`100.64.0.0/10`, `fd7a:115c:a1e0::/48`) | Tailscale (rule 12500, ahead of the VPN) |
| Accepted subnet routes | Tailscale, thanks to the module's *VPN coexistence* rules (12510) |
| Other internet traffic | the other VPN |
| Tailscale exit node | no effect until the other VPN is turned off |
| MagicDNS names | may not resolve (DNS goes through the VPN); use `100.x` addresses |

- VPN coexistence is on by default (`TS_VPN_COEXIST=1` in `settings.conf`); `tailscaled.service routes`
  lists the active rules. The app shows a summary card when another VPN is detected.
- Android *Private DNS* (DNS over TLS) bypasses Tailscale DNS, so MagicDNS does not work with it.
- A VPN's "Block connections without VPN" (lockdown) option may block tailnet traffic.

## Android app (Tailscaled Manager)

A Material 3 (Material You) companion app in [`android/`](android/). It uses root (`su`) to talk to
the module and the daemon; the module keeps doing the boot / background work.

- One switch to connect / disconnect (starts tailscaled when needed).
- Login: browser link (opened automatically), auth key, or a custom control server (Headscale).
  Add / switch account, log out, rename the device.
- Status: connection state, Tailscale IPs, MagicDNS name, account, health warnings, exit node in use.
- Devices: searchable list with online / exit node / subnet router filters; details sheet with
  IPs, routes, direct vs relay connection, traffic, ping and "use as exit node".
- Preferences: subnet routes, Tailscale DNS, Tailscale SSH, shields up, run as exit node, LAN access.
- Daemon: start / stop / restart, start on boot, daemon and service logs.
- Bundles the full module: if it is missing (or older), the app installs it through
  KernelSU (`ksud`), APatch (`apd`) or Magisk.

Build (JDK 21, Android SDK 37): put the binaries in `tailscale/bin/` to bundle the full module, then

```sh
cd android && ./gradlew assembleRelease
```

## Files

| Path | Purpose |
|---|---|
| `/data/adb/tailscale/bin/` | `tailscaled` (multicall, `tailscale` is a symlink), `jq` |
| `/data/adb/tailscale/scripts/` | service scripts |
| `/data/adb/tailscale/settings.conf` | user settings, kept across updates |
| `/data/adb/tailscale/tailscaled.state` | node state, kept across updates |
| `/data/adb/tailscale/run/tailscaled.log` | daemon log (`.bak` = previous run) |
| `/data/adb/tailscale/run/service.log` | service manager log |

## Settings

Edit `/data/adb/tailscale/settings.conf`, then run `su -c tailscaled.service restart`.

```sh
TS_DAEMON_ARGS="-no-logs-no-support"   # extra tailscaled flags
TS_AUTOSTART=1                         # start on boot
TS_STOP_TIMEOUT=10                     # seconds before SIGKILL on stop
TS_LOG_MAX_KB=2048                     # log trim threshold
TS_VPN_COEXIST=1                       # keep subnet routes on Tailscale while another VPN is on
```

## Available commands

- `tailscale`: the Tailscale CLI.
- `tailscaled`: the Tailscale daemon.
- `tailscaled.service`: service manager
  - `start` / `stop` / `restart`
  - `status [summary|peer|exit-node|all]`
  - `log [daemon|service]`
  - `info`: Android version, kernel, page size, root manager, binaries (include this in bug reports)
  - `routes`: subnet routes kept on Tailscale while another VPN is active

## Example of Using Tailscale

### SSH to Termux

You can use Tailscale to connect SSH from Termux on Android to a Windows PC. Here's how:

#### On your Android device:

1. Set up SSHD:

```bash
apt update && apt upgrade
apt install openssh
passwd
```

Enter your password when prompted, for example, `123`.

2. Run ssh daemon with command `sshd`
3. Get your IP with the command `tailscale ip` or check your IP in the [Tailscale Admin Dashboard](https://login.tailscale.com/admin/machines).

#### On your Windows PC:

1. Download & install [Tailscale for Windows](https://tailscale.com/download/windows)
1. Open app & login to the Tailscale.
3. Open the terminal & SSH to your Android IP:

```bash
ssh <root>@<tailscale_ip> -p 8022
```

For example:

```bash
ssh root@100.95.95.95 -p 8022
```

### SSH access to your Android device

You can also enable SSH access to your Android device using [Tailscale SSH](https://tailscale.com/kb/1193/tailscale-ssh?slug=kb&slug=1193&slug=tailscale-ssh). To do this, advertise SSH on the host with the command `tailscale up --ssh`.

By default, Tailscale's SSH feature may not work on Android because it requires `getent`, which is part of GNU libc, and relies on glibc-specific features like nsswitch.conf.

To overcome this, I've created a mock `getent` and placed it in `tailscale/bin/`. This mock `getent` is used by Tailscale's [userLookupGetent](https://github.com/tailscale/tailscale/blob/5812093d31c8a7f9c5e3a455f0fd20dcc011d8cd/util/osuser/user.go#L121C19-L121C33) function.

After advertising SSH on the host, you can SSH into your Android device using `ssh root@<tailscale_ip>`.

### ADB over Tailscale

You can run ADB over Tailscale. First, you need to enable ADB over TCP/IP. You can do this with the following commands:

```bash
setprop service.adb.tcp.port 5555
stop adbd
start adbd
```

These commands set the ADB daemon to listen on TCP port 5555 and then restart the ADB daemon to apply the change.

After enabling ADB over TCP/IP, you can connect to your Android device from your Windows machine using the `adb connect` command followed by your Tailscale IP and the port number:

```bash
adb connect <tailscale_ip>:5555
```

## Avalilable command

```
USAGE
  tailscale [flags] <subcommand> [command flags]

For help on subcommands, add --help after: "tailscale status --help".

This CLI is still under active development. Commands and flags will
change in the future.

SUBCOMMANDS
  up         Connect to Tailscale, logging in if needed
  down       Disconnect from Tailscale
  set        Change specified preferences
  login      Log in to a Tailscale account
  logout     Disconnect from Tailscale and expire current node key
  switch     Switches to a different Tailscale account
  configure  [ALPHA] Configure the host to enable more Tailscale features
  netcheck   Print an analysis of local network conditions
  ip         Show Tailscale IP addresses
  status     Show state of tailscaled and its connections
  ping       Ping a host at the Tailscale layer, see how it routed
  nc         Connect to a port on a host, connected to stdin/stdout
  ssh        SSH to a Tailscale machine
  funnel     Turn on/off Funnel service
  serve      Serve content and local servers
  version    Print Tailscale version
  web        Run a web server for controlling Tailscale
  file       Send or receive files
  bugreport  Print a shareable identifier to help diagnose issues
  cert       Get TLS certs
  lock       Manage tailnet lock
  licenses   Get open source license information
  exit-node

FLAGS
  --socket string
        path to tailscaled socket (default /var/run/tailscale/tailscaled.sock)
```

For more details about CLI commands, check out the [Tailscale CLI documentation](https://tailscale.com/kb/1080/cli#using-the-cli).

## FAQ & Troubleshooting

Tailscale has manny issues. You can check them out [here](https://github.com/tailscale/tailscale/issues).

### Exit nodes

You can check this [issue reference](https://github.com/anasfanani/Magisk-Tailscaled/issues/17).

### ipv6

Unfortunately, I'm verry lazy to learn ipv6.

### Headscale 

Check [this](https://github.com/anasfanani/Magisk-Tailscaled/issues/19#issuecomment-2091579177).
Also explore on the issue first, then you can ask trough telegram.


### Other Error & Bugs

You can explore to the issue tab, if there not exists, you can open issue, for help me resolve the problem, you can include fresh log.

1. Restart tailscaled with `tailscaled.service restart`
2. Reproduce what are you doing which has problem.
3. Attach the output of `tailscaled.service info` and the logs in `/data/adb/tailscale/run/`

## Notes

This module supports Magisk, KernelSU (and forks) and APatch. KernelSU support was originally [confirmed by the author of KernelSU](https://github.com/anasfanani/Magisk-Tailscaled/issues/2#issue-2055047162).

For more information, check out the links below:

## Links

- [Tailscale Userspace Networking](https://tailscale.com/kb/1112/userspace-networking/)
- [Termux Issue #10166](https://github.com/termux/termux-packages/issues/10166)
- [Tailscale Static Packages](https://pkgs.tailscale.com/stable/#static)
- [Tailscale Knowledge Base](https://tailscale.com/kb)

## Credits

- [Tailscale Inc & AUTHORS](https://github.com/tailscale/tailscale). for the static binaries of tailscale & tailscaled
- [John Wu & Authors](https://github.com/topjohnwu/Magisk). for The Magic Mask for Android
- [heiher & Authors](https://github.com/heiher/hev-socks5-tunnel). for the hev-socks5-tunnel

## Disclaimer

This module is provided as-is, I'm not employee at official tailscale, not a verry genius people which can resolve all your problem.
This module is not affiliated with the official Tailscale. It is a third-party implementation and the author is not responsible for any damage to your device that may occur from its use. Use at your own risk.
Any improvements is required, any PR is verry required, not just welcome.

## License

Released under [BSD 3-Clause License](/LICENSE).