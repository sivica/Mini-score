# Play Internal testing releases

Miniscore now uses the owner-approved package `com.ivicastojanoski.miniscore`, version **1.7.0**, version code **13**. This identity requires a new app in Play Console; its initial bundle must be uploaded manually before the publishing API can be used. The name remains **Miniscore**, with the approved mint-teal **Score pair** launcher icon. This build reads the Railway API; older builds retain the GitHub JSON URL. The replacement Play app has not been created or uploaded.

The selected logo is a white scoreboard with mint-teal `0:0` cutouts on a mint-teal background (`#62BEAF`), inspired by the owner’s reference logo. It is used in the app header, adaptive and legacy launcher icons, and the prepared [512px Play Store icon](branding/miniscore-play-icon.png). The [SVG source](branding/miniscore-score-pair.svg) has a full-square background. A separate monochrome drawable preserves the digit and colon cutouts for Android themed icons.

## First release published

Version **1.5.1**, code **7**, was uploaded manually and published to **Internal testing** on October 10, 2026. [Manage Miniscore in Play Console](https://play.google.com/console/u/0/developers/4637234217663254020/app/4975701497914028566/tracks/4701443907528615174?tab=releases). The source is commit `26df5f1`; bundle SHA-256 is `b8d75fbd408812791ff096e42ebb8b89c6aeae513e17606696ac316b359de194`.

At the owner's request on October 10, 2026, the old app's internal test was paused and the app was then **deleted** after explicit approval. Google verified the developer-registration receipt and package and confirms that the app can be recovered until **October 17, 2026**. After that deadline deletion is permanent. Existing installations remain on users' devices, but the old app is no longer available for new device downloads or Play updates. Production distribution was never configured. [Deletion status in Play Console](https://play.google.com/console/u/0/developers/4637234217663254020/app/4975701497914028566/advanced-distribution).

The package `com.aistudio.nbatracker.qfzwpm` belongs to the deleted app and must not be used to create a replacement Play app. The replacement uses `com.ivicastojanoski.miniscore`; it installs separately from the old app and has its own local cache. The build and publishing instructions below target the replacement. The registration transaction ID is private and is not stored in this repository.

The deleted app's release used the local private Miniscore upload key. The replacement keeps the existing Miniscore signing key. On October 10, the owner requested automatic uploads after merge and reuse of the SampleApp publishing account. All four signing secrets and the existing `sampleapp-play-publisher` credential are configured in GitHub, with `google-play-internal` restricted to `main` and no required reviewer. Credential contents remain private. The API credential was verified against SampleApp; Miniscore currently returns `Package not found`. The remaining prerequisites are merging to `main`, creating the replacement app, its initial Console upload, and granting the reused account access to this app if needed.

## Local release build

Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` (default `upload`), and `KEY_PASSWORD` in the build environment. Run Gradle 9.6.1 with Java 17 or newer and Android SDK platform 36.1 / build tools 36.0.0:

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

## Initial Console upload

Create **Miniscore**, English (United States), app category, free, using package `com.ivicastojanoski.miniscore`. The owner must confirm Google's Developer Program Policies and export-law declarations. Create an Internal testing release and upload the signed AAB. Complete Play App Signing enrollment and any required release declarations, then review and start the internal release. Tester access is managed separately in the track's Testers tab.

References: [SampleApp workflow](https://github.com/sivica/SampleApp/blob/main/.github/workflows/play-internal-release.yml), [upload action](https://github.com/r0adkll/upload-google-play), [GitHub environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).
