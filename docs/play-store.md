# Play Internal testing releases

Miniscore uses package `com.aistudio.nbatracker.qfzwpm`. Its first signed release is version **1.5.1**, version code **7**. The initial bundle must be uploaded manually to a new Miniscore app in Play Console before the publishing API can be used.

## First release published

Version **1.5.1**, code **7**, was uploaded manually and published to **Internal testing** on October 10, 2026. [Manage Miniscore in Play Console](https://play.google.com/console/u/0/developers/4637234217663254020/app/4975701497914028566/tracks/4701443907528615174?tab=releases). The source is commit `26df5f1`; bundle SHA-256 is `b8d75fbd408812791ff096e42ebb8b89c6aeae513e17606696ac316b359de194`.

At the owner's request on October 10, 2026, the old app's internal test was **paused** and the track is **Inactive**. Play Console confirms that testers are not receiving the release; resuming the track would restore access. The app record has not been deleted. Production distribution was never configured. Google says the pause usually propagates within an hour, but can take longer. [App availability settings](https://play.google.com/console/u/0/developers/4637234217663254020/app/4975701497914028566/advanced-distribution).

This release used the local private Miniscore upload key. No signing or Play credentials were transferred to GitHub. The workflow below is prepared for future automation and still requires owner-approved secret/environment configuration and merging to `main` before use.

## Local release build

Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` (default `upload`), and `KEY_PASSWORD` in the build environment. Run Gradle 9.6.1 with Java 17 or newer and Android SDK platform 36.1 / build tools 36.0.0:

```sh
gradle --no-daemon --no-configuration-cache testDebugUnitTest bundleRelease
```

The bundle is `app/build/outputs/bundle/release/app-release.aab`. Upload keys and signing configuration must remain private; `.play-release/`, keystores and service-account files are ignored. Keep a secure backup of the upload key and its passwords.

## GitHub workflow

`.github/workflows/play-internal-release.yml` follows SampleApp's build-then-publish workflow. It runs manually, builds and tests an AAB, saves it as an artifact, then publishes to **Internal testing** through `google-play-internal`. It does not publish to Production.

Before enabling automated uploads, configure the environment with `sivica` as required reviewer and restrict deployment to `main`. Configure repository signing secrets:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`
- `ANDROID_UPLOAD_KEYSTORE_PASSWORD`
- `ANDROID_UPLOAD_KEY_ALIAS`
- `ANDROID_UPLOAD_KEY_PASSWORD`

Configure `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` as a secret of the approval-gated `google-play-internal` environment. The service account must already have permission to publish this app to testing tracks. Credential transfer and access changes require the owner's approval.

Merge the workflow and app changes to `main`. From Actions, run **Build and publish Play internal release** on `main`, supplying the desired version name and a fresh integer version code higher than every previous Play upload. After tests and signing succeed, inspect the AAB artifact and approve the pending publish job. Retry a failed publish using the same workflow run when appropriate; do not reuse an already uploaded version code for a different bundle.

## Initial Console upload

Create **Miniscore**, English (United States), app category, free, using its existing package name. The owner must confirm Google's Developer Program Policies and export-law declarations. Create an Internal testing release and upload the signed AAB. Complete Play App Signing enrollment and any required release declarations, then review and start the internal release. Tester access is managed separately in the track's Testers tab.

References: [SampleApp workflow](https://github.com/sivica/SampleApp/blob/main/.github/workflows/play-internal-release.yml), [upload action](https://github.com/r0adkll/upload-google-play), [GitHub environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).
