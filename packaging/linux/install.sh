#!/usr/bin/env bash
#
# G95NC Controller - installer/updater for the portable Linux build (.tar.gz).
#
# Installs the app for the current user only: no root, no package manager, so it also works on
# immutable distros (Bazzite, Silverblue, SteamOS...). Ships inside the release archive and gets
# copied into the install folder along with the app, so the installed copy can update itself.
#
# Usage:
#   ./install.sh                 Install the app in the folder this script sits in, then add it to
#                                the start menu. On first install, asks where to put it (default
#                                ~/Apps/G95NC-Controller) and whether to start it on login.
#                                Run from a newer release, it replaces the installed version in
#                                place, without asking again.
#   ./install.sh --update        Download and install the latest GitHub release, if it's newer
#                                than the installed one. Keeps your folder and autostart choice.
#   ./install.sh --uninstall     Remove the app, its start menu entry and its autostart entry.
#                                Your settings/presets (~/.config/g95nc-controller) are kept.
#
# Options (to skip the questions, e.g. when scripting):
#   --dir PATH                     Install into PATH (moves an existing install there).
#   --autostart / --no-autostart   Start (or don't start) the app automatically on login.
#
# Environment:
#   G95NC_INSTALL_DIR   Same as --dir.
#   GITHUB_TOKEN        Optional, only for --update: a token to call the GitHub API with (avoids
#                       anonymous rate limits).
#
# What gets written (and removed by --uninstall):
#   <install folder>/                                      the app itself (incl. this script)
#   ~/.local/share/applications/g95nc-controller.desktop   start menu entry (also how this
#                                                          script remembers the install folder)
#   ~/.local/share/icons/g95nc-controller.png              start menu icon
#   ~/.config/autostart/g95nc-controller.desktop           only if you opted into autostart (runs
#                                                          start-on-login.sh - see that file)

set -euo pipefail

REPO="ema987/g95nc-ddc-app"
APP_NAME="G95NC Controller"
ASSET_SUFFIX="linux-x64.tar.gz"
DEFAULT_INSTALL_DIR="$HOME/Apps/G95NC-Controller"

DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
CONFIG_HOME="${XDG_CONFIG_HOME:-$HOME/.config}"
DESKTOP_FILE="$DATA_HOME/applications/g95nc-controller.desktop"
ICON_FILE="$DATA_HOME/icons/g95nc-controller.png"
AUTOSTART_FILE="$CONFIG_HOME/autostart/g95nc-controller.desktop"

# The folder this script lives in - i.e. the unpacked app it will install.
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Where the app is (or will be) installed - decided in resolve_install_dir().
INSTALL_DIR=""

info() { printf '%s\n' "$*"; }
die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

# Expands a leading ~ and makes a path absolute, without requiring it to exist yet.
normalize_path() {
    local path="$1"
    case "$path" in
        "~") path="$HOME" ;;
        "~/"*) path="$HOME/${path#"~/"}" ;;
    esac
    case "$path" in
        /*) ;;
        *) path="$PWD/$path" ;;
    esac
    printf '%s' "${path%/}"
}

# The folder of the current install, read back from the Exec= line of the start menu entry this
# script wrote last time. Empty if not installed (or installed some other way).
previous_install_dir() {
    [ -f "$DESKTOP_FILE" ] || return 0
    sed -n "s|^Exec=\"\(.*\)/bin/$APP_NAME\"\$|\1|p" "$DESKTOP_FILE" | head -n 1
}

# Picks the install folder. In order: --dir / G95NC_INSTALL_DIR, the existing install, then (only
# for a first install) the user's answer, defaulting to ~/Apps/G95NC-Controller.
resolve_install_dir() {
    local action="$1" requested="$2" previous
    previous="$(previous_install_dir)"

    if [ -n "$requested" ]; then
        INSTALL_DIR="$requested"
    elif [ -n "$previous" ]; then
        INSTALL_DIR="$previous"
    elif [ "$action" != "install" ]; then
        # Never guess here (e.g. "the folder this script is in"): that could be a freshly
        # unpacked download, and --uninstall would delete it.
        die "Couldn't find an installed $APP_NAME (no start menu entry at $DESKTOP_FILE). Pass --dir if it's installed somewhere specific."
    elif [ -t 0 ]; then
        local answer shown="~${DEFAULT_INSTALL_DIR#"$HOME"}"
        read -r -p "Install folder [$shown]: " answer
        INSTALL_DIR="${answer:-$DEFAULT_INSTALL_DIR}"
    else
        INSTALL_DIR="$DEFAULT_INSTALL_DIR"
    fi
    INSTALL_DIR="$(normalize_path "$INSTALL_DIR")"
}

# Reads the version jpackage baked into an app folder's launcher config (e.g. "1.0.3"). Prints
# nothing (and still succeeds, so set -e doesn't abort) if the folder isn't an installed app.
app_version() {
    sed -n 's/^java-options=-Djpackage.app-version=//p' "$1"/lib/app/*.cfg 2>/dev/null | head -n 1 || true
}

# True if a folder is missing, empty, or contains this app - i.e. safe for us to rm -rf.
# Guards against wiping something else, e.g. if you answer "~" or "~/Apps" for the folder.
is_ours_or_empty() {
    [ ! -e "$1" ] || [ -z "$(ls -A "$1" 2>/dev/null)" ] || [ -x "$1/bin/$APP_NAME" ]
}

ensure_safe_install_dir() {
    is_ours_or_empty "$INSTALL_DIR" ||
        die "$INSTALL_DIR already exists and doesn't contain $APP_NAME - refusing to overwrite it. Pick a new or empty folder."
}

# Only one instance may talk to the monitor at a time, and replacing files under a running app
# can crash it - so refuse to touch an install that's currently running.
ensure_not_running() {
    if pgrep -f "$1/bin/$APP_NAME" > /dev/null 2>&1; then
        die "$APP_NAME is running. Quit it from its tray menu, then run this again."
    fi
}

# Writes a .desktop file ($1) that runs $2. Exec is quoted because the launcher's filename contains
# a space. StartupWMClass is the window class Java derives from the main class, so the desktop can
# match the app's window to this entry (right icon in the taskbar/dock).
write_desktop_file() {
    mkdir -p "$(dirname "$1")"
    cat > "$1" <<EOF
[Desktop Entry]
Type=Application
Name=$APP_NAME
Comment=Control Samsung Odyssey G95NC layouts, inputs and audio
Exec="$2"
Icon=$ICON_FILE
Categories=Utility;Settings;
Terminal=false
StartupWMClass=com-g95nc-controller-MainKt
EOF
}

# Autostart goes through start-on-login.sh, which waits for the display to be ready at login -
# starting the app directly can race the desktop and crash it (see that script).
write_autostart_file() {
    write_desktop_file "$AUTOSTART_FILE" "$INSTALL_DIR/start-on-login.sh"
}

# Some desktops only pick up new entries after their cache is refreshed; harmless if missing.
refresh_menu() {
    if command -v update-desktop-database > /dev/null 2>&1; then
        update-desktop-database "$DATA_HOME/applications" > /dev/null 2>&1 || true
    fi
}

do_install() {
    local autostart_choice="$1"

    [ -x "$SOURCE_DIR/bin/$APP_NAME" ] ||
        die "No app found next to this script ($SOURCE_DIR/bin/$APP_NAME). Run it from the unpacked release folder."
    [ "$(uname -m)" = "x86_64" ] || die "This build is for x86_64 Linux only (this machine is $(uname -m))."

    local previous new_version old_version
    previous="$(previous_install_dir)"
    new_version="$(app_version "$SOURCE_DIR")"
    old_version="$(app_version "${previous:-$INSTALL_DIR}")"

    if [ "$SOURCE_DIR" != "$INSTALL_DIR" ]; then
        ensure_safe_install_dir
        ensure_not_running "$INSTALL_DIR"
        [ -n "$previous" ] && ensure_not_running "$previous"
        # Copy to a temporary sibling first, then swap it in: an interrupted copy never leaves a
        # half-installed app behind.
        mkdir -p "$(dirname "$INSTALL_DIR")"
        rm -rf "$INSTALL_DIR.new"
        cp -a "$SOURCE_DIR" "$INSTALL_DIR.new"
        rm -rf "$INSTALL_DIR"
        mv "$INSTALL_DIR.new" "$INSTALL_DIR"
    fi

    # Installed somewhere else before (--dir to a new folder): remove the old copy, but only if
    # it's really the app.
    if [ -n "$previous" ] && [ "$previous" != "$INSTALL_DIR" ] && [ "$previous" != "$SOURCE_DIR" ] &&
        [ -x "$previous/bin/$APP_NAME" ]; then
        rm -rf "$previous"
        info "Moved the install from $previous."
    fi

    mkdir -p "$(dirname "$ICON_FILE")"
    cp "$INSTALL_DIR/lib/$APP_NAME.png" "$ICON_FILE"
    write_desktop_file "$DESKTOP_FILE" "$INSTALL_DIR/bin/$APP_NAME"
    refresh_menu

    # Autostart: an explicit flag wins; otherwise ask, but only on a terminal. Without one (e.g.
    # piped/scripted), keep whatever is already set up.
    if [ -z "$autostart_choice" ] && [ -t 0 ]; then
        local default="n" prompt="[y/N]" answer
        if [ -f "$AUTOSTART_FILE" ]; then default="y"; prompt="[Y/n]"; fi
        read -r -p "Start $APP_NAME automatically when you log in? $prompt " answer
        case "${answer:-$default}" in
            [Yy]*) autostart_choice="yes" ;;
            *) autostart_choice="no" ;;
        esac
    fi
    case "$autostart_choice" in
        yes) write_autostart_file ;;
        no) rm -f "$AUTOSTART_FILE" ;;
        *) if [ -f "$AUTOSTART_FILE" ]; then write_autostart_file; fi ;; # refresh, keep as-is
    esac

    if [ -n "$old_version" ] && [ "$old_version" != "$new_version" ]; then
        info "Updated $APP_NAME $old_version -> $new_version."
    else
        info "Installed $APP_NAME $new_version."
    fi
    info "  App:        $INSTALL_DIR"
    info "  Start menu: $DESKTOP_FILE"
    if [ -f "$AUTOSTART_FILE" ]; then info "  Autostart:  on"; else info "  Autostart:  off"; fi
}

do_update() {
    command -v curl > /dev/null 2>&1 || die "--update needs curl."

    local installed latest_json latest_tag latest_version url
    installed="$(app_version "$INSTALL_DIR")"
    [ -n "$installed" ] || die "$APP_NAME isn't installed in $INSTALL_DIR - run ./install.sh from an unpacked release first."

    local auth=()
    if [ -n "${GITHUB_TOKEN:-}" ]; then auth=(-H "Authorization: Bearer $GITHUB_TOKEN"); fi
    latest_json="$(curl -fsSL ${auth[@]+"${auth[@]}"} -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/$REPO/releases/latest")" ||
        die "Couldn't reach GitHub to check for updates."

    # Plain grep/sed instead of jq, which isn't installed everywhere.
    latest_tag="$(printf '%s' "$latest_json" | grep -m 1 '"tag_name"' | sed 's/.*"tag_name": *"\([^"]*\)".*/\1/')"
    url="$(printf '%s' "$latest_json" | grep '"browser_download_url"' | grep -- "-$ASSET_SUFFIX\"" |
        head -n 1 | sed 's/.*"browser_download_url": *"\([^"]*\)".*/\1/')"
    latest_version="${latest_tag#v}"
    [ -n "$latest_version" ] && [ -n "$url" ] || die "Couldn't find a Linux release download on GitHub."

    # sort -V orders versions numerically (1.0.10 after 1.0.9); if installed sorts last, we're current.
    if [ "$(printf '%s\n%s\n' "$latest_version" "$installed" | sort -V | tail -n 1)" = "$installed" ]; then
        info "$APP_NAME $installed is already the latest version."
        return
    fi

    ensure_not_running "$INSTALL_DIR"
    info "Updating $APP_NAME $installed -> $latest_version..."
    UPDATE_TMP="$(mktemp -d)"
    trap 'rm -rf "$UPDATE_TMP"' EXIT
    curl -fL --progress-bar -o "$UPDATE_TMP/app.tar.gz" "$url" || die "Download failed."
    tar -xzf "$UPDATE_TMP/app.tar.gz" -C "$UPDATE_TMP"

    local new_installer
    new_installer="$(find "$UPDATE_TMP" -mindepth 2 -maxdepth 2 -name install.sh | head -n 1)"
    [ -n "$new_installer" ] || die "The downloaded release has no install.sh."

    # Run the *new* release's installer, so updates always use the latest install logic. Pass the
    # folder and autostart state through so nothing is asked again on update.
    local keep="--no-autostart"
    if [ -f "$AUTOSTART_FILE" ]; then keep="--autostart"; fi
    bash "$new_installer" --dir "$INSTALL_DIR" "$keep"
}

do_uninstall() {
    [ -x "$INSTALL_DIR/bin/$APP_NAME" ] || die "$APP_NAME isn't installed in $INSTALL_DIR."
    ensure_not_running "$INSTALL_DIR"
    rm -rf "$INSTALL_DIR"
    rm -f "$DESKTOP_FILE" "$ICON_FILE" "$AUTOSTART_FILE"
    refresh_menu
    info "Uninstalled $APP_NAME from $INSTALL_DIR. Settings/presets in ~/.config/g95nc-controller were"
    info "kept - delete that folder too if you want them gone."
}

main() {
    local action="install" autostart="" requested_dir="${G95NC_INSTALL_DIR:-}"
    while [ $# -gt 0 ]; do
        case "$1" in
            --update) action="update" ;;
            --uninstall) action="uninstall" ;;
            --autostart) autostart="yes" ;;
            --no-autostart) autostart="no" ;;
            --dir)
                [ $# -ge 2 ] || die "--dir needs a folder."
                requested_dir="$2"
                shift
                ;;
            -h|--help) sed -n '2,/^$/s/^# \{0,1\}//p' "${BASH_SOURCE[0]}"; exit 0 ;;
            *) die "Unknown option: $1 (see --help)" ;;
        esac
        shift
    done

    resolve_install_dir "$action" "$requested_dir"
    case "$action" in
        install) do_install "$autostart" ;;
        update) do_update ;;
        uninstall) do_uninstall ;;
    esac
}

main "$@"
