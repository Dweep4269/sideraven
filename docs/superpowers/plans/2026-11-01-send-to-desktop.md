# Send to Desktop Implementation Plan

**Goal:** Send a normal phone tab to its shared Zen Space, keeping the phone copy.

**Spec:** `docs/superpowers/specs/2026-11-01-send-to-desktop-design.md`

**Architecture:** A durable account-bound queue owns stable record IDs. The existing sync runner serializes sends and ordinary sync. A dedicated uploader creates normal-tab records and conditionally appends their IDs to Space children, leaving normal tabs outside the regular merge.

**Tech stack:** Kotlin, Compose, SharedPreferences, existing encrypted Sync 1.5 transport, Robolectric/JUnit.

## Tasks

- [x] Add failing `SendToDesktopTest` cases for normal-tab payloads, retaining existing Space children, repeated sends/retries, blocked destinations/capabilities, and regular sync after sending. Run with `./gradlew -PtestBuildType=forkDebug -PsideravenTests app:testForkDebugUnitTest --tests 'pt.dweep.sideraven.sync.SendToDesktopTest'`.
- [x] Add `DesktopSendQueue.kt`: pending send records and in-memory/preferences stores. Bind each send to a hash of the account scoped key; commit pending records before upload. Use a stable record ID across retries.
- [x] Extend `ZenConnection` to read the encrypted synced normal-tabs preference using the prefs collection key. Extend `ZenSync` to validate/enqueue sends and upload pending records after ordinary sync, with conditional writes and desktop snapshot refresh. Preserve unknown Space fields and container identity. Never resurrect tombstoned sends.
- [x] Extend `ZenSyncRunner` to enqueue/send under its existing mutex, report status, and retain failed work for later sync.
- [x] Add `SidebarIntent.SendToDesktop`, menu eligibility, handler callback and host wiring. Show queued/uploaded/failure feedback. Never close or navigate the phone tab. Add handler tests covering private tabs and preserving the phone copy.
- [x] Run focused sync and sidebar tests, compile the app, inspect the diff and document any environment or desktop verification limits.

## Verification constraints

The initial Gradle baseline fails while resolving `mozac.DependenciesPlugin`: the unprepared android-components checkout references a version catalog outside this worktree. Investigate the documented preparation steps before evaluating feature tests. Do not commit generated checkout-preparation changes.

## Verification result

- App Kotlin and Java compilation passed with the repository's warnings-as-errors configuration.
- Focused sync and tab-menu run: 151 tests passed.
- Regression negative check: temporarily removing the outbound children fix made `a local Space edit after sending preserves the new desktop child` fail with the sent ID missing. The fix was restored.
- Full Sideraven suite: `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew -PtestBuildType=forkDebug -PsideravenTests app:testForkDebugUnitTest --tests 'pt.dweep.sideraven.*' --console=plain` passed: 666 tests, zero failures, errors, or skips.
- Temporary build-preparation edits and generated files were removed after verification; prepare a fresh checkout before rebuilding as described in README.
- A live Zen desktop receipt has not been exercised; automated checks verify encrypted storage records, desktop snapshot refresh, retries, and menu behavior.

## Instant-send revision (2026-11-02)

- Publish the loading row synchronously and expand On desktop at the moment of tapping.
- Upload the explicit copy ahead of unrelated merging, prepend it to desktop Space children, and immediately run a full sync even with automatic sync off.
- Keep previews until a confirmation snapshot succeeds; deduplicate partial server rows by record identity and coalesce repeated taps.
- Negative checks reproduced bottom placement, disappearing copies after a failed confirmation download, and repeated-tap duplicate previews.
- Updated full Sideraven test suite (678 tests, zero failures/errors/skips) and debug APK build both pass. Tablet update uses ADB install -r to retain app data.
