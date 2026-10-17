# Visualization MVP acceptance record

Verified October 11, 2026 on the isolated `codex/miniscore-visualizations` branch based on `b71eeda`. Saved-snapshot reopening is explicitly excluded. No release upload or merge was performed as part of implementation.

## Completed checks

| Check | Result and scope |
| --- | --- |
| Kotlin/native contract, feed/cache, session and existing app regressions | 62 unit tests passed, 0 failed or skipped |
| Packaged JavaScript renderer | 6 Node DOM tests passed; synthetic 80-player bound, literal labels, null/zero distinction, FG calculations and ordering |
| Android builds | `assembleDebug` and `assembleDebugAndroidTest` passed |
| Static analysis | `lintDebug` and `lintVitalRelease` passed; debug lint retains warnings, including deliberate JavaScript use and a renderer-process override false positive verified by the device termination test |
| Actual packaged WebView | 4 instrumented tests passed on API 36 / WebView 133.0.6943.137: literal-label/native-derived FG parity and CSP-denied fetch; unavailable-provider fallback; renderer termination; an injected infinite render reaches the native two-second fallback. Failure tests also verify native Close |
| Full app explicit refresh | SAS–PHX captured game and field-goal selection retained after fetching published revision 3 |
| Offline refresh | Airplane-mode refresh reports failure and retains chart/table and existing timestamps |
| Offline process reopening | Scores reopen from cache; a newly opened chart reports Saved game data/revision 3 with a new capture time. The old field-goal session is not reopened |
| Configuration/font change | Captured game and field-goal spec survived 200% font scaling; native controls reflowed and the source-table percentage moved onto a second line without horizontal clipping |
| Independent source review | Changed-default selection and unavailable-provider crash findings fixed; no remaining concrete source-level blocker reported |

The hang test changes native spec state while the same injected WebView stays visible, and asserts the exact timeout message. It does not rely on scrolling away and accidentally replacing the renderer. The CSP fetch probe proves a denied connection in that case; it is not a comprehensive proof of all network paths. One `uiautomator` capture process exited 137 during manual checks; its retry succeeded and the app remained running. No hardware-memory conclusion is inferred.

The dedicated emulator's font scale was restored to `1.0` and airplane mode to `0` after testing. Its RAM is approximately 2.5 GiB; it is not a genuine low-memory device. Screenshots and raw test/lint records are held in the local `artifacts/miniscore-visualizations-mvp` evidence directory outside the repository.

## Reproduce

Use the repository's Android SDK/JDK/Gradle setup and public debug key:

```sh
gradle --no-daemon --no-configuration-cache testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug lintVitalRelease
node --test app/src/test/js/renderer.test.cjs
```

Install both generated debug APKs onto a dedicated emulator, then run:

```sh
adb shell am instrument -w -r -e class com.example.VisualizationDeviceTest com.ivicastojanoski.miniscore.test/androidx.test.runner.AndroidJUnitRunner
```

## Open acceptance gates

IVI-20 and IVI-24 retain these gates before beta/release readiness:

- Actual supported low-memory phone; cold-start, interaction/render latency, peak memory and CPU/memory-exhaustion budgets measured on final integrated code.
- Minimum-supported API 24 and older supported WebView provider behavior, including feature-unavailable native fallback.
- Full TalkBack traversal/spoken label-value pairing and native dropdown keyboard arrows/Home/End, focus/reset, reduced-motion and broader font/device combinations. Screenshots alone do not verify spoken output.
- Complete hostile-renderer runtime/network matrix: redirects and unmatched paths, frames/popups/workers, permissions, downloads, external intents and forged/oversized/duplicate messages. Unit policy tests and the single actual CSP fetch test are partial evidence.
- Complete import-picker usability testing on device, beyond strict decoder, bounded UTF-8 reader and atomic session import tests.

These are open validation tasks, not passes inferred from code review or the earlier separate spike. This feature remains a draft implementation until those gates and merge/release decisions are handled.
