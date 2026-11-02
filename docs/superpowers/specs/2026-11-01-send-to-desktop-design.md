# Send a phone tab to desktop

## Intent

Let the user send a normal phone tab to the matching Zen desktop Space while keeping the phone tab open. This is a one-time copy of its current URL and title; later navigation or closing on either device does not affect the other copy.

## Behavior

- Add **Send to desktop** to the phone tab's context menu.
- Offer the action for normal web tabs in shared Spaces with Zen Sync configured. Private tabs and Local Spaces are excluded.
- Send into the same Space on desktop. Zen must support and enable normal-tab syncing (`zen.spaces-sync.normal-tabs`). Read the synced preference to distinguish support from whether desktop currently has any open tabs.
- Keep the phone tab open throughout sending, success, and failure.
- Show the copy immediately at the top of **On desktop**, expand the section, and show a loading indicator until upload is confirmed. This preview must appear before disk, credentials, network work, or waiting for another sync.
- Retain a durable pending send with a stable record ID so network retries do not create extra desktop tabs. Report queued/failed/uploaded state without claiming desktop has received the tab merely because storage accepted it. Match server confirmation by record ID so another tab at the same URL cannot swallow the preview.
- Create a normal Zen tab record (`pinned: false`) and prepend its identity to the destination Space's children, preserving existing records and ordering with conditional writes. Serialize sending with regular sync; recover partial writes using the same pending identity.
- Prioritize the explicit tab upload over unrelated merging, then trigger a full sync immediately regardless of the automatic-sync setting or debounce timer. A new Space must sync before its copy can be uploaded.
- Refresh the desktop snapshot after upload so the copied tab appears in **On desktop**. Zen opens it when desktop next syncs.

## Approaches

The conversation selected sending a copy rather than moving the phone tab. Full two-way open-tab sync is unnecessary for this request. A one-time normal-tab upload uses the existing Zen Space relationship and desktop section; a separate device command would require a different receiving path and would not itself populate this section.

## Constraints and validation

The current desktop snapshot is read-only, normal tabs are excluded from regular merge, and outbound Space updates preserve their identities as foreign children. Sending must preserve that isolation: subsequent regular sync must neither delete nor recreate a sent tab after desktop closes it.

Test destination validation, private-tab exclusion, capability disabled/absent/enabled, retaining the local tab, same-ID retries after partial failure, concurrent Space edits, and normal sync after sending. Verify desktop receipt against Zen before claiming end-to-end delivery; local fake-server tests establish only the wire behavior.

## Implementation choices proposed

Use the existing Kotlin/Compose context menu, encrypted Zen storage, and sync runner. Implement a dedicated pending-send store and upload operation rather than broadening the normal-tab merge scope. No live session, history, cookies, or ongoing tab identity link is transferred.

Wire reference checked during implementation: [Z-Sync's SpacesSyncService](https://github.com/Kjell6/Z-Sync/blob/main/android/app/src/main/java/de/kjell/zencompanion/sync/SpacesSyncService.kt), specifically `syncedPrefs`, `tabCleartext`, and `addTab`. Its normal-tab capability fixture is already vendored in this repository. Sideraven refuses unsupported normal-tab sends rather than falling back to pinning.
