# Contributing to AnimeVostBR

Thanks for your interest in improving AnimeVostBR! Bug reports, ideas and pull requests are welcome.

## Reporting bugs

Open an [issue](https://github.com/Maxic112/AnimeVostBR/issues) and include:

- the app version (shown at the bottom of Settings) and Android version;
- the device type (phone, tablet or Android TV);
- steps to reproduce, what you expected and what happened;
- if content does not load: a screenshot of **Settings → Connection check** — it shows which part
  (API, video, site, mirror) is failing.

Please do not attach personal data, logs with account information or signing keys.

## Development setup

Requirements: JDK 17 and the Android SDK (platform 34).

```bash
git clone https://github.com/Maxic112/AnimeVostBR.git
cd AnimeVostBR
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. Project structure, data sources and the
release process are described in [docs/technical.md](docs/technical.md) (in Russian); the API
reference is in [docs/api.md](docs/api.md).

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep changes focused; one feature or fix per pull request.
3. Make sure `./gradlew testDebugUnitTest assembleRelease` passes. CI runs the same checks.
4. Add or update tests when changing parsers (`core/Models.kt`, `core/SiteSource.kt`). Real responses
   for tests go to `app/src/test/resources/fixtures`, reduced to the data the test needs.
5. Check both form factors when touching the UI: touch on a phone and D-pad navigation on Android TV
   (every interactive element must be reachable and show the focus border).
6. Describe what changed and why in the pull request; update `CHANGELOG.md` under an
   "Unreleased" section.

## Code style

- Follow the existing Kotlin style (official Kotlin coding conventions, 4-space indentation).
- Code comments are in English and explain *why*, not *what*: keep them for non-obvious behaviour,
  API quirks and platform workarounds.
- User-facing strings are in Russian.
- Never commit signing keys, `keystore.properties`, `local.properties` or other machine-specific files.

## Releases

Releases are built and signed by GitHub Actions when a `v*` tag is pushed; only maintainers create tags.
