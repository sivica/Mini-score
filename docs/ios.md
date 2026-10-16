# Miniscore for iPhone and iPad

Native SwiftUI app for iOS/iPadOS 17 or later. The iOS app shares the Railway feed and daily import with Android; changing game statistics does not require another App Store build. The bundle identifier is `com.ivicastojanoski.miniscore`, version 1.0.0 (1).

## Build and test

Open `ios/Miniscore.xcodeproj` in Xcode 16 or later. Select the Miniscore scheme and an iPhone/iPad simulator, then Run. Xcode 26 is used for local validation. There are no third-party dependencies.

```sh
swift test --package-path ios
xcodebuild -project ios/Miniscore.xcodeproj -scheme Miniscore \
  -configuration Release -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ios/DerivedData CODE_SIGNING_ALLOWED=NO build
```

`ios/tools/generate-project.py` recreates the checked-in Xcode project using Python 3; run it after adding source files. `swift ios/tools/generate-icon.swift ios/Miniscore/Assets.xcassets/AppIcon.appiconset/AppIcon.png` recreates the opaque 1024px icon from the existing original scoreboard geometry.

## Behavior

- The centered original mint logo, three-letter team codes, score, date and Final disclosure match Android. Venue and quarter scores expand below the score.
- A native game-selection sheet supports search, date groups and the selected game. Player sheets show the full name and additional stats. No team artwork, player photos or unfinished navigation is included.
- Pull down to refresh. The app also refreshes on reopening. Names and minutes/seconds sit above the two stat rows; substitutes sort by total seconds played.
- Dynamic Type scales names and values from 14pt and labels from 12pt. Accessibility text sizes stack full-width player cards with team labels. Colors adapt to light and dark appearances, with VoiceOver summaries and native controls.
- The fixed HTTPS Railway endpoint uses bounded responses (2 MiB), same-host HTTPS redirects and conditional ETags. Validation rejects floating-point/exponent numeric literals, booleans for integers, invalid arrays, duplicate IDs, inconsistent final scores and invalid bounds before replacing a cache.
- A single atomic file stores the last validated feed and ETag. Rejected downloads and network failures preserve it. Selection is stored separately. Offline/bundled status appears above the scoreboard as well as in the footer. The bundled October 15 snapshot is only a first-launch fallback and is identified as a bundled snapshot until a successful update.

## Simulator checks

Debug builds support launch environment `MINISCORE_UI_TEST=1` to use the bundled real-game fixture in an isolated `Miniscore-UI-Test` cache. Add `MINISCORE_TEST_OFFLINE=1` on a later launch to simulate an unavailable network and verify cached reopening. These controls and the fixture transport are excluded from Release builds. Normal launches use the production API.

Check game search/switching, Final disclosure, player details, pull-to-refresh, persistence after relaunch and offline reopening. Also check accessibility text sizes and both appearances. Simulator checks do not replace testing a signed TestFlight build on a physical device.

## Signing and TestFlight

Apple team: `36HS92C4DU`. App Store Connect app record: `6823642590`. Automatic signing is configured; signing credentials and provisioning profiles must stay outside Git. The repository deliberately contains no API key, private signing key or Apple account password.

1. Sign into Xcode with the developer account or configure an approved App Store Connect upload credential.
2. Select the team and a generic iOS device; create/reuse an authorized Apple Distribution certificate and App Store provisioning profile for the bundle ID.
3. Product → Archive → Distribute App → App Store Connect → Upload. The local release also supports `xcodebuild -exportArchive` with an ignored `ios/.ios-release/ExportOptions.plist` containing the approved team, certificate and manual profile. Increase `CURRENT_PROJECT_VERSION` for subsequent uploads. A signed archive can be built without registered test devices by using the existing Apple Distribution identity and `Miniscore App Store` manual profile; do not create another certificate automatically just to rebuild.
4. After Apple processes the build, add it to an internal TestFlight group and test on an iPhone/iPad. External testers may require Beta App Review.
5. Complete screenshots, description, support/privacy URLs, age rating, privacy answers, pricing/availability and review information before an App Store submission. App creation or TestFlight upload does not publish to the public store.

No automatic Apple upload runs on merge. Add CI signing only after a secure credential method is approved.

## Privacy and release inputs

The client has no analytics, advertising SDK, tracking, account login, photos or location access. It uses Apple networking and local app-support files; the privacy manifest declares no tracking or collected data in the client. Backend request/IP logging and Apple's TestFlight diagnostics must be assessed separately when answering App Store privacy questions. See the existing [privacy policy](https://sivica.github.io/Mini-score/privacy/) and [policy inputs](policy-inputs.md).

The statistics-source licence remains unresolved, as already recorded in the policy audit. MIT/source-code licensing does not grant rights to third-party statistics. Do not affirm content rights or submit a rights declaration without the owner's supplied confirmation.

## Local validation record (October 16, 2026)

- Seven Swift Testing functions pass (including parameterized integer/array cases). Rejected feeds and network errors preserve identical cache bytes, ETag and decoded data; a restarted repository reads them correctly.
- Debug simulator build and unsigned Release device archive succeed in Xcode 26.6.
- iPhone 17 Pro / iOS 26.5: live Railway feed, search by NYK, game switching, quarter disclosure, player sheet and isolated simulated-offline relaunch verified. Selected TOR–NYK game survives reopening, with an explicit saved-data banner.
- Light/dark appearances and accessibility-extra-large Dynamic Type (stacked cards) inspected. This is Apple's named accessibility category, not an exact percentage setting.
- The desktop Simulator input bridge did not deliver drag/scroll gestures reliably. Pull-to-refresh is implemented with native SwiftUI `refreshable`, but the gesture still needs an on-device/TestFlight check. Do not describe that manual gesture check as passed.
- Apple membership, App Store Connect and Xcode account access verified. With owner approval, an Apple Distribution certificate and the `Miniscore App Store` provisioning profile were created on October 16, 2026 (both expire October 16, 2027). The private key is in the Mac login Keychain; the temporary private-key file was removed after import. No API key was created or committed.
- A signed Release archive for version 1.0.0 (1) passes `codesign --verify --deep --strict`; its embedded profile matches `36HS92C4DU.com.ivicastojanoski.miniscore` and has debugging disabled. App Store Connect accepted the upload at 17:09 Europe/Skopje on October 16, 2026 (`xcodebuild` reported `Upload succeeded` and `EXPORT SUCCEEDED`). Apple then processes the build before it can be assigned to internal testers. No public App Store release was submitted.
