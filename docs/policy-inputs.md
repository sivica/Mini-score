# Miniscore policy inputs and content audit

Tracked in [IVI-10](https://linear.app/ivicastojanoski/issue/IVI-10/provide-miniscore-policy-inputs-and-content-rights-confirmation). Audited October 10, 2026 against the code 1002 source on `main` and the proposed drawer privacy link.

## Owner-approved direction

The owner accepted GitHub Pages hosting for the policy, a teen-and-adult audience, and a content-permission audit. The intended audience is ages **13–15, 16–17, and 18+**. This is an audience decision, not an IARC rating or proof of compliance with rules for minors in every launch country. The Play Console declaration is a separate task in IVI-11. The owner subsequently confirmed no existing licences and authorized original badges and generic avatars, with the statistics-source licence remaining unresolved.

The policy source is [`privacy/index.html`](../privacy/index.html). Its public URL is `https://sivica.github.io/Mini-score/privacy/`. The repository's existing Pages configuration publishes the root of `develop`; changes to this page on that branch do not trigger the `main` Android release workflow. Keep the page synchronized when the app's practices change.

## Data-handling findings

| Area | Evidence and implication |
| --- | --- |
| Account and permissions | `AndroidManifest.xml` requests INTERNET only. The public app has no account creation or sign-in. Owner dashboard credentials are not public-app credentials. |
| Game requests | `GameFeedRepository.kt` uses OkHttp to GET the Railway `/games` endpoint on opening/resuming and refresh. It sends no user profile, search string or selected-game ID. The service receives connection metadata, including an IP address. |
| Image requests | Existing code 1002 uses Coil `AsyncImage` for feed-provided HTTPS logos and portraits; image hosts receive IP addresses and request metadata. This PR replaces rendering with app-owned badges/avatars, removes Coil and all 32 bundled NBA images, and ignores feed image URLs for display. |
| Local state | `filesDir/games-cache.json` holds the last valid feed; existing releases can cache images; Compose maintains selection/search state. No per-user search history is stored by the backend. Old image caches may remain until cleared or the app is uninstalled; the proposed app does not display them. |
| Backup | `allowBackup=true`; current backup XML does not exclude the feed file. Android may back up/transfer eligible app data. Do not promise uninstall deletes all cloud/device-backup copies. |
| Ads, purchases and analytics | No active ads, billing, Firebase Analytics or Crashlytics integration was found in application code/dependencies. The Firebase BOM and logging-interceptor dependency alone do not activate those SDKs. |
| Dormant AI code | `GeminiClient` and an analysis panel exist, but `MainNbaScreen` does not expose or invoke them. If AI is enabled later, re-audit and update the policy before release. |
| Server logs | Docker starts Uvicorn with default access logging. Operational logs may include IP, path, status and time. Railway documents seven days of visible log history for Hobby, not a guarantee that all provider records are permanently deleted after seven days. No log exporter was found in source; production overrides were not inspected. |
| Support | The owner-approved public contact is `s.ivica@gmail.com`. Email contents and sender details are handled by the email provider. The policy gives retention criteria and a contact for access/correction/deletion requests. |

Do not prefill Data safety as “no data collected” from the absence of accounts or analytics. Classify actual network/log processing and third-party handling using Google's current definitions and any applicable exceptions before completing IVI-11. Hosting-provider dashboards, SDK runtime behavior and support-record practices must remain consistent with this policy.

## Content provenance and rights

| Content | Source | Current evidence |
| --- | --- | --- |
| Automated game feed | ESPN scoreboard/summary endpoints in `scripts/update_game_feed.py` | Source attribution exists; no redistribution licence or written permission was found. |
| Bundled fallback stats | NBA result/quarters and RealGM player box score, referenced in `PreseasonGameData.kt` | Source attribution exists; permission remains unverified. |
| Bundled images | Two team logos and 30 player photos from NBA CDN, inventoried in `nba-image-sources.json` | Provenance is recorded; no permission documents were found. |
| Remote images | ESPN CDN and NBA CDN URLs in feed fixtures/seed | URLs are not licence evidence. |
| Play listing | Saved screenshots/feature graphic contain team logos and player photos | Rights checks must cover Store assets as well as the APK. Recreate those assets if the images are replaced. |
| Miniscore identity | Original mint-teal score-pair vector/code assets | App-owned artwork; this does not grant rights to third-party sports content. |

NBA terms restrict public redistribution without permission. Google Play also identifies unauthorized sports logos and professional photos as possible intellectual-property violations. This audit records missing evidence; it does not establish a legal entitlement or declare infringement.

### Decisions still needed

- The owner selected the image replacement path. This PR implements original mint-teal team-code badges and generic avatars, without remote image loads or bundled photos. The earlier Play build and saved Store graphics still contain the old images. Rebuild/revalidate the app and regenerate Store screenshots/feature graphic before using this change for Production; do not treat code 1002 as remediated.
- Separately establish an authorized statistics source. Removing images, entering data manually or adding attribution does not by itself resolve data-source rights.
- A paid provider must explicitly permit the planned use. A subscription or accessible endpoint is not sufficient evidence that logos/photos and redistribution are included.
- Do not mark IVI-10 done until the public URL is verified, audience approval is recorded, and content rights or an acceptable replacement source are established.

## References

- [Google Play privacy-policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play audience guidance](https://support.google.com/googleplay/android-developer/answer/9867159)
- [Google Play intellectual property](https://support.google.com/googleplay/android-developer/answer/9888072)
- [NBA terms](https://www.nba.com/termsofuse)
- [Disney / ESPN terms](https://disneytermsofuse.com/english/)
- [Railway log retention](https://docs.railway.com/observability/logs)
- [GitHub Pages and IP logging](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages)
