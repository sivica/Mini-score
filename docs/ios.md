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
- A signed Release archive for version 1.0.0 (1) passes `codesign --verify --deep --strict`; its embedded profile matches `36HS92C4DU.com.ivicastojanoski.miniscore` and has debugging disabled. App Store Connect accepted the upload at 17:09 Europe/Skopje on October 16, 2026 (`xcodebuild` reported `Upload succeeded` and `EXPORT SUCCEEDED`). Apple processed the build; it is **Testing** in **Miniscore Internal**, with the owner as its one approved tester. No public App Store release was submitted.

## App Store submission draft (October 16, 2026)

Public metadata is versioned in [`ios/AppStore/en-US.json`](../ios/AppStore/en-US.json). Private App Review contact information is stored only in App Store Connect.

- Version **1.0.0**, build **1**, remains **Prepare for Submission**. The build is attached and manual release is selected.
- Saved English listing: Miniscore; subtitle **Basketball box scores**; category **Sports**; promotional text, description, keywords, copyright, support URL and reviewer notes. No login is required. The listing describes published daily data, not live play-by-play.
- Saved screenshots: one 1206×2622 iPhone screenshot, two 1320×2868 large-iPhone screenshots and two 2064×2752 iPad screenshots. Images show the real app, contain no team artwork/player photos, and use opaque JPEGs.
- Age questionnaire: no objectionable content, social features, advertising, gambling or unrestricted browsing. Calculated 4+ overridden to **13+**, matching the existing teen/adult audience policy (regional equivalents apply).
- Pricing: **Free** in all currencies; availability configured for all **175 countries or regions**, taking effect only after approval and manual release. Account compliance and regional eligibility can still restrict actual availability.
- Public [support](https://sivica.github.io/Mini-score/support/) and [privacy](https://sivica.github.io/Mini-score/privacy/) pages were published on the existing `develop` GitHub Pages source and verified. Privacy coverage now includes iPhone/iPad, Apple backups and TestFlight.
- Privacy responses **published on October 16 with explicit owner approval**: **Other Diagnostic Data**, **App Functionality**, **linked to user**, **not used for tracking**. Production Railway HTTP logs expose source-IP, user-agent, request and response diagnostic fields; application access-log samples contain private/proxy IP addresses. Railway documents seven days of visible log retention on Hobby, which is not a guarantee of deletion of every provider record. The disclosure accounts for retained operational diagnostics without assuming anonymization. No location is inferred from IP in app code. The client has no telemetry SDK; support email is optional and user-initiated outside the app. See [Apple's disclosure guidance](https://developer.apple.com/app-store/app-privacy-details/) and [Railway's log fields and retention](https://docs.railway.com/observability/logs). Keep the responses updated when practices change. A client privacy manifest does not replace this server disclosure.
- The signed TestFlight build has a saved five-step **What to Test** checklist covering game selection persistence, pull-to-refresh, offline reopening, player/quarter details, text/appearance and the privacy link.
- Digital Services Act compliance is **Active** as of October 16. The owner confirmed Miniscore is a personal, non-commercial hobby outside their business/professional work, and the **non-trader** declaration was saved. Apple reports all current regulatory requirements completed. No public trader address or phone was supplied. The existing Free Apps Agreement is Active; the Paid Apps Agreement was not accepted.

Before public submission:

- Test signed TestFlight build on a physical device: game switching, pull-to-refresh, reopen with saved games offline, and support/privacy links. The desktop gesture bridge did not verify pull-to-refresh.
- Resolve the existing statistics-source rights issue before affirming Content Rights. It remains deliberately unanswered; source-code MIT licensing does not grant data rights.
- Review the completed submission and explicitly approve sending it to App Review. Adding a build or saving metadata does not submit it.
