# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning](https://semver.org/).

## [1.4.0] - 2026-11-03

### Added
- Picture format in the player: pinch out to fill the screen and pinch in to restore the original
  size on phones; a format button in the player bar cycles Original, Fill (crop) and Stretch, also
  with a TV remote. The chosen format is remembered.

### Changed
- A long episode name in the player bar is shortened instead of pushing the buttons off screen.

## [1.3.0] - 2026-11-02

### Added
- In-app updates from GitHub Releases: Settings → Update checks for a new version, downloads it and
  opens the system installer. The app checks once a day in the background.
- Orange dot on the Settings icon and on the Update item when a new version is available.

## [1.2.0] - 2026-11-02

### Added
- Site setting: auto (main site, then mirror), main site only, or mirror only.
- Automatic discovery of the current site mirror (redirect from the old address, links on the
  main site, RSS, next mirror numbers), on start, in the background and on demand.
- Fallback data source: when the API is unavailable, the feed, title cards and episodes are loaded
  from the site or its mirror.
- "Connection check" screen with 12 checks covering the API, video and image hosts, site, mirror
  and the fallback source.
- `tools/api_check.py` for monitoring API availability and response format.

### Changed
- Network errors are explained in plain language.
- Settings buttons are easier to reach with a TV remote.

### Fixed
- No empty rating or stale "of N" episode count for titles loaded from the site.

## [1.1.0] - 2026-11-02

### Added
- Title header with Russian and English names, tag chips and tap-to-expand for long names.
- Pull-to-refresh on phones and a refresh button on Android TV.
- "Tiles per row" setting and an episode list with frame previews.
- Full Android TV remote support: player controls, visible focus border, focus restoration.
- YouTube-style double-tap seeking and auto-hiding player bar.
- "Continue watching" tab with item removal, search suggestions while typing.
- New launcher icon and Android TV banner.

### Changed
- Minified, signed release build (about 4 MB).

### Fixed
- Automatic switch to SD when the HD file is missing on the server.

## [1.0.1] - 2026-11-02

### Fixed
- The feed now loads the whole catalogue instead of a single title.
- Search uses the correct API parameter.
- SD playback, episode names and numbering.
- New-episode notifications are limited to favorites and open the title.
- Text visibility in the dark theme; posters load again.

## [1.0.0] - 2026-11-01

### Added
- First version: feed, search, title details, built-in ExoPlayer, favorites, continue watching
  and new-episode notifications.

[1.2.0]: https://github.com/Maxic112/AnimeVostBR/releases/tag/v1.2.0
