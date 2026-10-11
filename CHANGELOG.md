# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

When releasing, rename `[Unreleased]` to the new version and date, then push the matching tag -
the release workflow uses that section as the GitHub release notes.

## [Unreleased]

## [1.0.0] - 2026-10-11

First public release.

### Added
- Control the Samsung Odyssey G95NC's PBP/PIP layouts, input sources and audio source over DDC/CI,
  from a dashboard window or straight from the system tray / menu bar.
- Presets: save, apply, rename, reorder and delete layout + source combinations.
- Safe Mode: changes auto-revert after a countdown unless confirmed.
- Light, dark and follow-system themes.
- Lives in the tray / menu bar: on macOS it has no Dock icon, and on Linux it keeps running in the
  tray when the dashboard is closed.
- Start at login, quietly in the tray / menu bar: a Settings option on macOS, and an option of the
  Linux portable build's `install.sh`.
- Installers for macOS (Apple Silicon and Intel `.dmg`), Windows (`.msi`, untested) and Linux
  (`.deb`, `.rpm`), plus a portable Linux `.tar.gz` for immutable distros (Bazzite, Silverblue,
  SteamOS...) whose `install.sh` adds a start menu entry and supports `--update` and `--uninstall`.

[Unreleased]: https://github.com/ema987/g95nc-ddc-app/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ema987/g95nc-ddc-app/releases/tag/v1.0.0
