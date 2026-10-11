#!/usr/bin/env bash
#
# G95NC Controller - login launcher, used only by the autostart entry install.sh writes.
#
# Why this exists: desktops like KDE Plasma start autostart apps as systemd user units, and can do
# so before the session has published DISPLAY/XAUTHORITY to systemd. The app draws through X11
# (XWayland on Wayland desktops), and Java decides once at startup whether a display exists - so
# starting it too early makes it crash with a HeadlessException, and it never retries.
#
# So: wait (up to ~30s) until systemd's user environment has a DISPLAY, import it, then start the
# app. If DISPLAY is already set (e.g. a desktop that autostarts apps directly), it starts at once.
# The app starts with --background: just the tray icon, no dashboard window popping up at login.

set -u

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for _ in $(seq 1 60); do
    if [ -z "${DISPLAY:-}" ] && command -v systemctl > /dev/null 2>&1; then
        # XAUTHORITY too: XWayland may only accept clients presenting its auth cookie.
        while IFS='=' read -r name value; do
            case "$name" in
                DISPLAY|XAUTHORITY) export "$name=$value" ;;
            esac
        done < <(systemctl --user show-environment 2>/dev/null)
    fi
    [ -n "${DISPLAY:-}" ] && break
    sleep 0.5
done

# Start regardless: without a display it fails exactly as before, and the error lands in the
# journal (journalctl --user -b | grep -i g95nc).
exec "$APP_DIR/bin/G95NC Controller" --background "$@"
