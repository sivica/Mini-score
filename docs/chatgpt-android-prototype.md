# ChatGPT plan usage in Miniscore (local prototype)

This branch adds **Continue with ChatGPT** and a native prompt composer to Charts, using the documented public-client dynamic-registration flow for open-source/local apps. It depends on the visualization MVP in PR #5. It is an opt-in debug prototype, not a verified production integration.

Build with `gradle :app:assembleDebug -PchatGptEnabled=true`. The feature is disabled without this property. Release builds always set `CHATGPT_ENABLED=false`, even with that property. No API key, client secret, Railway proxy, browser-cookie extraction or shared account is used.

The opt-in APK uses package `com.ivicastojanoski.miniscore.chatgptprototype` and launcher label **Miniscore ChatGPT test**, so it installs alongside the Play app without replacing it.

## User flow

1. Open a game with player statistics, then **Charts**.
2. Tap **Continue with ChatGPT**. Complete account/workspace selection and consent in the system browser, then return to Miniscore. The device's `127.0.0.1` callback waits up to five minutes; cancellation or process death ends the attempt.
3. The first plan-enabled sign-in explains usage once. Choose from the signed-in account's model catalog.
4. Enter “Compare rebounds” or “Show field goals” and tap **Generate chart**. The composer discloses that the prompt, current chart settings, game ID, team codes, player IDs and names go directly to OpenAI. No statistical values, feed, credentials, other games or chart HTML are uploaded.
5. Only validated settings for the captured game are applied. Native data supplies all statistics. Unsupported filters, scripts, new data, mismatched games and incomplete/refused/failed responses retain the previous chart.
6. **Manage usage** opens ChatGPT usage settings. Separate account/workspace registrations can be selected or reconnected. Sign-out attempts remote revocation and clears local tokens. If revocation cannot be confirmed, the UI explains how to disconnect in ChatGPT settings. Account/client mappings and host identity remain for reconnecting.

The composer supports the existing player-stat and field-goal templates, team/single-player filters and sorting. Arbitrary generated interfaces, starter-only filtering, cross-game queries, chat history, background requests and billing fallbacks are excluded. Manual chart controls remain usable without sign-in.

## Boundaries

- Browser OAuth uses fresh state, nonce and PKCE S256. Exact callback host/port/path, duplicate parameters, replay, expiry, returning client ID and verified identity are checked. Issued registrations survive code-exchange failure.
- ID-token signatures use fresh OpenAI JWKS; exact issuer/audience, multiple-audience authorized party, nonce, expiry and issued-at are checked. Only RS256 is accepted, matching public metadata verified October 11, 2026; other algorithms fail closed.
- Android Keystore AES-GCM protects atomic credential records in `noBackupFilesDir`. Tokens never enter Compose/saved state, logs, Railway or WebView. Secure-storage failure disables connection instead of falling back to plaintext. The stable host ID remains local.
- Refresh is serialized, uses the issued client, retains scope when omitted and replaces rotating credentials together. Confirmed terminal refresh failures clear tokens; temporary failures preserve them. No alternate billing is silently used.
- Account-specific models come from `/v1/models`. Inference uses `/v1/responses`, `store:false`, `stream:true`, no redirects, cookies, logging interceptors, polling or automatic retries. Requests arise only from Generate. Cancellation closes calls while consuming bodies/streams. Size and time are bounded.
- A completed SSE response is required, with final text matching streamed text. A late usage-limit error still rejects partial output. The existing 16 KiB strict chart contract rejects arbitrary scripts/data and invalid game/player bindings.
- Chart edits, imports, refresh, close and game switching cancel generation. A settings revision rejects output even after filters change and change back. Scrolling the composer away does not cancel requests; rotation retains the ViewModel connection and pending request.

## Verification and remaining gates

Unit tests cover callback/PKCE, signed identity claims, permission/expiry, account isolation, streamed errors, output limits and chart retention/stale results. Device tests use synthetic credentials to verify Keystore encryption, reopen, tamper rejection, stable host identity and a real Android loopback socket callback. They never log in to an OpenAI account and do not prove plan entitlement.

The repository includes an MIT licence with the owner's explicit approval. Before release enablement: confirm eligibility; complete real eligible-account authorization, model discovery and generation on Android; test minimum API 24 and background/process recovery; review authentication and approved sign-in branding; update privacy/Data safety disclosures for user-requested OpenAI requests. No partner agreement was accepted. The Play candidate is unchanged.

## Official documentation

- [Availability](https://developers.openai.com/siwc/quickstart)
- [Registration and verification](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [Accounts, refresh and revocation](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
- [Models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [UI/usage controls](https://developers.openai.com/siwc/ui-ux-guidelines)
- [Preview restrictions](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [Errors/recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
