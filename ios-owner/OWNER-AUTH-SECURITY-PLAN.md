# Owner POS Authentication & Security Plan

Status: repository-source audit only; no live Firebase project or deployed Firestore rules have been verified.

## Findings from the Android source (main, 2026-11-17)

- The Android cloud module uses Firebase Authentication and Cloud Firestore when `window.LP_FIREBASE.apiKey` and `projectId` are non-empty.
- Its username convention maps a username to `<username>@laprincesa-pos.app`.
- The role record is read from `roles/{uid}`.
- If that role record is missing and `meta/owner` is also missing, the Android module attempts to create the first owner marker and an owner role record. This is a first-owner bootstrap path, not a safe iOS owner-login flow.
- The Android role helper caches role information in localStorage and can fall back to that cache after some non-permission errors. The iOS Owner app must not trust a local cached role as authorization.
- The cloud module includes write-capable functions for POS data synchronization, staff creation/removal, and cloud data deletion. The iOS app must not import or call these functions.
- The source's cloud hydration reads the Firestore `data` collection. No `onSnapshot` listener was found in the checked source, so this implementation does not provide a continuous live listener.
- No Firestore Security Rules or verified Firebase runtime configuration were found in the repository during this audit.

## Required Owner client behavior

1. Use Firebase Authentication only after the actual Firebase project configuration is verified.
2. After sign-in, read `roles/{uid}` from the server and require an explicit `role == "owner"`. A missing role, staff role, permission error, or network failure must deny access.
3. Never bootstrap or create the first owner from the iOS app.
4. Never treat localStorage, a previously cached role, hidden buttons, or client-side route guards as authorization.
5. Read only the explicitly approved POS records. Do not write to `data`, `roles`, `meta`, or any Staff POS collection.
6. Enforce owner read-only access in Firestore Security Rules. Test that the Owner identity is denied create, update, and delete operations even if requests are made outside the UI.
7. If the actual database is not verified, keep the app in an explicit disconnected state and do not display invented or stale figures as current.
8. When live connection is eventually enabled, use server-backed listeners and show connection state plus last successful sync time. A local cached snapshot must be labeled stale/offline.

## Separation requirements

- Android Staff POS remains the operational system of record.
- Owner iOS is a separate monitoring client.
- Do not modify Android source, APK workflows, printer integration, receipt-print order, or cash-drawer behavior for this work.
- Do not merge or deploy the Owner client without explicit approval.

## Release blockers

- [ ] Confirm the Firebase project/configuration actually used by the installed Android POS.
- [ ] Confirm the real Firestore document schema and the exact owner UID.
- [ ] Obtain and review the deployed Firestore Security Rules.
- [ ] Test Owner read access and denied writes against a non-production/test dataset.
- [ ] Verify all Owner totals against Android records, including unpaid/partial payments, voids/deletions, expenses, shift movements, and timezone cutoffs.
- [ ] Verify listener reconnect and stale/offline indicators.
- [ ] Confirm Android source and build workflow remain unchanged.
