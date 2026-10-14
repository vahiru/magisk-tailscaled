## v3.0.0.0

### Rewrite: Android 16 / 17 and KernelSU
- Supports Android 8 (API 26) through Android 17 (API 37).
- Works on Magisk, KernelSU (incl. KernelSU Next / SukiSU) and APatch.
- KernelSU without a metamodule: command wrappers are also installed into the
  `su` PATH (`/data/adb/ksu/bin`, `/data/adb/ap/bin`), so nothing depends on `system/` being mounted.
- `system/bin` now holds small wrapper scripts instead of symlinks into `/data`.
- New **Action** button: toggles tailscaled and shows its status.
- Enabling / disabling the module starts / stops tailscaled instantly
  (busybox `inotifyd` when available, polling fallback otherwise).
- Daemon tracked by PID file + `/proc`, no busybox `pgrep`/`pkill`/`less` needed;
  graceful SIGTERM with configurable timeout before SIGKILL.
- Daemon started detached (`setsid`) so it survives the installer / su shell.
- Installer verifies `tailscaled` actually runs on the device (wrong ABI / 16 KB page size)
  and that `jq` is usable; `status` falls back to plain output without jq.
- User settings moved to `/data/adb/tailscale/settings.conf` and preserved on update.
- Updates keep node state in place instead of moving files into `backups/`.
- Removed the volume-key prompt and the `/data/adb/service.d` general script (cleaned up on update).
- New `tailscaled.service info` command for bug reports.
- Logs are trimmed automatically.

## v2.0.0.1

- 429e1b0 build: fix version bump and add pre-release for build version
- 75834b4 fix: path order

## v2.0.0.0

### ⚠️ Important Notice
This build may be unstable. Please test thoroughly and report any issues you encounter.

**Exit Node Warning**: Using exit nodes may cause battery drain. Not recommended for 24/7 use.

**Testing Your Connection**:
- Check IP: https://browserleaks.com/ip
- Check DNS leaks: https://browserleaks.com/dns

### Major Rewrite
This is a complete rewrite of the module with significant improvements.

### What's New
- **Better DNS handling** - Improved DNS configuration and compatibility
- **SSH support** - Enhanced SSH functionality
- **Exit node support** - Better exit node handling
- **Hotspot client support** - Works with Android hotspot clients
- **VPN compatibility** - Can now run alongside other VPN apps
- **Root & Non-root mode** - Works in both root and non-root environments

### Technical Changes
- Removed all wrapper scripts for better performance
- Now uses tailscaled binaries from [tailscale-android-cli](https://github.com/anasfanani/tailscale-android-cli)
- Simplified module structure
- Improved stability and reliability

### Breaking Changes
This is a major version update. Please backup your configuration before upgrading.
