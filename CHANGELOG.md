# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning](https://semver.org/).

## [1.8.1] - 2026-11-08

### Fixed
- The app crashed when opening "Connection check". Compose 1.7 from 1.8.0 did not work with the
  Compose compiler for Kotlin 1.9 and failed while the screen added result rows; Compose and the
  libraries tied to it are back to the versions of 1.7.0 (Compose BOM 2024.02). Media3 1.4.1 stays.
- Android no longer warns that the app does not support 16 KB memory pages: the native library that
  came with Compose 1.7 is gone, so the APK has no native code again.

## [1.8.0] - 2026-11-08

### Added
- Schedule: upcoming episodes of the favorites by day, from the calendar icon on the home screen.
- Downloads for offline viewing in any quality, via the system download manager: "Download episodes"
  on the title page (or a long press on an episode on phones), Settings → Downloads to watch and delete.
  Downloaded episodes play from storage.
- Phone ↔ TV sync of favorites and watch progress over the local network with a 6-digit code; the
  waiting device is found automatically (NSD) or by address.
- Quality setting for mobile (metered) networks.
- Player: quality menu instead of cycling, "Skip 90 s" during the first minutes, a countdown to the next
  episode, picture-in-picture on phones, headset buttons and system media controls (media session).
- Daily source health check in GitHub Actions, now also covering the site player page.

### Fixed
- Playback went on in the background after leaving the app (Home on the TV remote, switching apps).
- Unplugging headphones kept playing through the speaker.

### Changed
- Media3 1.4.1, Compose BOM 2024.09; deprecated APIs replaced. Pull-to-refresh is phone-only: on TV it
  left its indicator over the feed, and the header button refreshes.
- Player decisions (quality, spare links, countdown) moved to `PlaybackRules` with unit tests.

## [1.7.0] - 2026-11-08

### Added
- Average rating on poster tiles in the feed and search, e.g. "★ 4,3".
- A spinner while the player fetches a link from the site.

### Changed
- When a video fails, the player first tries other servers of the same quality from the site player
  and fetches fresh links if the old ones are over 5 minutes old (links expire after about 3 hours,
  e.g. during a long pause). Only then it switches to a lower quality.
- Player page requests time out after 6 seconds per site instead of 15.

## [1.6.0] - 2026-11-04

### Added
- Full HD (1080p) quality in Settings, on the title page and in the player. The link comes from the
  site player, so it works through the mirror too. Episodes without Full HD play in HD with a notice,
  and the next episode tries Full HD again.
- Full HD check on the "Connection check" screen.

## [1.5.0] - 2026-11-03

### Added
- Browse by genre, type and year: while the search field is empty, Search shows 27 genres, 7 types
  and release years; each opens a listing from the site with infinite scroll.
- List layout for the feed, search and catalog listings: poster with title, year, type, episode
  count, genres, rating and description, plus Watch/Continue and favorite buttons. Poster size: auto
  or 1–5; smaller sizes show less text.

### Changed
- The episode-list setting is renamed to "Episodes on the title page" with a hint, so it is not
  mistaken for the feed layout.
- On TV, Search opens with focus on the genres instead of the on-screen keyboard; returning from a
  listing no longer pops the keyboard up on phones.

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
