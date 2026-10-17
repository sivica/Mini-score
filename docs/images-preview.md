# Images and positions preview

Branch: `codex/team-player-images`. This branch is for private Android testing; it is not merged into production or submitted to a store.

The debug APK installs as **Miniscore Preview**, package `com.ivicastojanoski.miniscore.preview`, version `1.7.0-images-preview`. It installs alongside the Play version and has its own app data. Uninstalling it does not remove the Play app or its cache. It uses the repository's existing public debug key, not the production signing key.

- Team logos appear above three-letter codes on the scoreboard and in the game drawer.
- Circular player photos appear beside jersey/position/minutes, mirrored between teams. Names remain one line and statistics retain their label/value rows.
- Images load from validated HTTPS URLs already supplied by the Railway feed, with Coil memory/disk caching and neutral fallbacks.
- Scores and stats continue to use production Railway. No Railway service, production feed or backend code changes are needed for this branch.
- Player positions are optional metadata fetched from the selected game's ESPN summary, using the existing Miniscore feed user-agent. Finished-game metadata is cached separately for up to 100 games and restored offline. Only matching player IDs are enriched; scores, minutes, rosters and the validated feed cache cannot be replaced by this metadata request. Unavailable positions are omitted rather than guessed.

The preview therefore makes additional direct requests to ESPN/CDN services, which receive network connection metadata. It does not establish rights to distribute the statistics, team logos or player photos. Those rights remain unresolved; this preview is not a production/store release.

Build with Java 21 and the existing Android SDK:

```sh
base64 --decode debug.keystore.base64 > debug.keystore
gradle --no-daemon --no-configuration-cache testDebugUnitTest assembleDebug lintVitalRelease
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. The `debug.keystore` and local SDK configuration remain ignored.

An opt-in live smoke test checks that actual image bitmaps enter Coil's cache, positions are restored into the lineup, and the resulting screen is captured on the dedicated emulator. It is separate from offline CI:

```sh
gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.MediaPreviewSmokeTest
```

The unit/UI suite covers metadata caching/offline restoration, wrong-game rejection with exact cache preservation, player-ID matching, existing-feed position precedence, normal layout and 200% font layouts. Missing images/metadata do not block scores or game selection.
