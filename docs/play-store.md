# Play Internal testing releases

Miniscore now uses the owner-approved package `com.ivicastojanoski.miniscore`, version **1.7.0**, version code **13**. The replacement app was created and its initial bundle published to **Internal testing** on October 10, 2026. [Manage the replacement release in Play Console](https://play.google.com/console/u/0/developers/4637234217663254020/app/4974662155591421319/tracks/4701569367017935626?tab=releases). The name remains **Miniscore**, with the approved mint-teal **Score pair** launcher icon. This build reads the Railway API; older builds retain the GitHub JSON URL. Google displays a temporary package-based name until app setup and review are complete.

The initial replacement bundle was built from commit `bf6c2085059a0546a87d7110ed925a6d05793a49`, with SHA-256 `a057f9e155e9ad5765f220420effb2b5efd2f28cd56e9efb93e35d8f1bf445d4`. After owner approval, the existing **Ivica internal tester** list was enabled and the track is **Active**. The reused service account has app-scoped publishing access; the Google Play API confirms the matching bundle hash and completed internal release.

The selected logo is a white scoreboard with mint-teal `0:0` cutouts on a mint-teal background (`#62BEAF`), inspired by the owner’s reference logo. It is used in the app header, adaptive and legacy launcher icons, and the prepared [512px Play Store icon](branding/miniscore-play-icon.png). The [SVG source](branding/miniscore-score-pair.svg) has a full-square background. A separate monochrome drawable preserves the digit and colon cutouts for Android themed icons.

## First release published

Version **1.5.1**, code **7**, was uploaded manually and published to **Internal testing** on October 10, 2026. [Manage Miniscore in Play Console](https://play.google.com/console/u/0/developers/4637234217663254020/app/4975701497914028566/tracks/4701443907528615174?tab=releases). The source is commit `26df5f1`; bundle SHA-256 is `b8d75fbd408812791ff096e42ebb8b89c6aeae513e17606696ac316b359de194`.

At the owner's request on October 10, 2026, the old app's internal test was paused and the app was then **deleted** after explicit approval. Google verified the developer-registration receipt and package and confirms that the app can be recovered until **October 17, 2026**. After that deadline deletion is permanent. Existing installations remain on users' devices, but the old app is no longer available for new device downloads or Play updates. Production distribution was never configured. [Deletion status in Play Console](https://play.google.com/console/u/0/developers/4637234217663254020/app/4975701497914028566/advanced-distribution).

The package `com.aistudio.nbatracker.qfzwpm` belongs to the deleted app and must not be used to create a replacement Play app. The replacement uses `com.ivicastojanoski.miniscore`; it installs separately from the old app and has its own local cache. The build and publishing instructions below target the replacement. The registration transaction ID is private and is not stored in this repository.

The deleted app's release used the local private Miniscore upload key. The replacement keeps the existing Miniscore signing key. On October 10, the owner requested automatic uploads after merge and reuse of the SampleApp publishing account. All four signing secrets and the existing `sampleapp-play-publisher` credential are configured in GitHub, with `google-play-internal` restricted to `main` and no required reviewer. Credential contents remain private. The owner approved **View app information (read-only)** and **Release apps to testing tracks** for the replacement app, which also includes the automatically selected read-only app quality permission. Production and admin permissions were not granted. A temporary API edit successfully read the bundle and tracks, then was deleted. The remaining activation step is merging the workflow to `main`; a real GitHub publishing run has not occurred yet.

## Local release build

Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` (default `upload`), and `KEY_PASSWORD` in the build environment. Run Gradle 9.6.1 with Java 21 and Android SDK platform 36.1 / build tools 36.0.0. The Android 36 Robolectric tests require Java 21, so both PR checks and publishing use it:

```sh
gradle --no-daemon --no-configuration-cache testDebugUnitTest bundleRelease
```

The bundle is `app/build/outputs/bundle/release/app-release.aab`. Upload keys and signing configuration must remain private; `.play-release/`, keystores and service-account files are ignored. Keep a secure backup of the upload key and its passwords.

## GitHub workflow

`.github/workflows/play-internal-release.yml` follows SampleApp's build-then-publish workflow. Every push to `main`, including a merged PR, tests the Android app, runs release lint, builds a signed AAB, saves it as an artifact, then automatically publishes to **Internal testing** through `google-play-internal`. Direct pushes to `main` also release. Daily stats updates on Railway or the legacy `game-data` branch do not trigger an APK release. It does not publish to Production. A manual run on `main` remains available, with an optional version name.

Restrict the `google-play-internal` environment to `main`, without a required reviewer, so merged releases upload automatically as requested. Configure repository signing secrets:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`
- `ANDROID_UPLOAD_KEYSTORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_ALIAS`
- `ANDROID_UPLOAD_KEY_PASSWORD`

Configure `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` as a secret of `google-play-internal`. The service account must have permission to publish `com.ivicastojanoski.miniscore` to testing tracks. GitHub cannot export the encrypted credential from SampleApp; reusing that account needs its original private credential file or a newly generated key for the same account. No private credential belongs in the repository.

Merge the workflow and app changes to `main`. Each release uses version code **1000 + GitHub workflow run number**, starting above the existing code 13. Default version names append the run number to the app version, such as `1.7.0.1`. The optional repository variable `PLAY_VERSION_CODE_OFFSET` changes the offset; never lower it, and raise it before releasing if a manual Play upload used a higher code. The allocator validates Android's 2100000000 limit and rejects invalid inputs.

The whole release workflow queues up to 100 runs and never cancels an active upload. Re-running a workflow keeps its version code. If publishing fails before the bundle is uploaded, retry the failed publish job to reuse the stored AAB. After a later release or an ambiguous/partly completed upload, inspect Play Console and start a fresh workflow run if another bundle is needed; do not reuse an uploaded code for a different bundle. Signed artifacts are retained for 14 days.

Pull requests run `.github/workflows/play-release-checks.yml` to validate release syntax and version allocation without signing or Play secrets. Local checks:

```sh
python -m unittest scripts.tests.test_play_release_version -v
python scripts/play_release_version.py --run-number 1
```

`main` requires a pull request, up-to-date successful **Android PR checks**, **Backend checks** and **Release workflow checks** from GitHub Actions, plus resolution of review conversations. Administrators are included; force pushes and branch deletion are blocked. No additional collaborator approval is required, so the owner can review and merge their own PR after the checks pass. There are no bypass actors. The Internal testing workflow still publishes automatically after that merge.

All three checks run on every PR, without path filters that could leave required checks pending. Android PR CI uses only the public debug key and runs unit tests, a debug APK build and release vital lint. Signing/publishing secrets remain restricted to the post-merge workflow. Backend CI includes real PostgreSQL transaction/advisory-lock and backup restore tests, importer tests and dashboard browser regressions.

## Initial Console upload

The replacement **Miniscore** app uses English (United States), app category, free, and package `com.ivicastojanoski.miniscore`. The owner confirmed Google's Developer Program Policies and export-law declarations before creation. The signed version 1.7.0 / code 13 AAB was uploaded, reviewed and published to Internal testing. Tester access is managed separately in the track's Testers tab. The new tester join link is https://play.google.com/apps/internaltest/4701569367017935626; it requires an enabled tester list and opt-in by an eligible account.

References: [SampleApp workflow](https://github.com/sivica/SampleApp/blob/main/.github/workflows/play-internal-release.yml), [upload action](https://github.com/r0adkll/upload-google-play), [GitHub environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).
