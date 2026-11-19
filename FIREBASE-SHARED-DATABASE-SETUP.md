# La Princesa POS — Firebase shared database setup

This is the setup plan for the Android Staff POS and iOS Owner PWA. The checked-in code uses Firebase Authentication and Cloud Firestore. The prior Supabase project was not connected to this repository, so this implementation continues with Firebase as previously selected for La Princesa.

## Current source status

- Android source now loads the public Firebase Web App config from `firebase-config.js`.
- The file now contains the confirmed public Web App configuration for project `laprincesa-pos`.
- The Android cloud module uses the existing Firestore collection `data` and document IDs based on `laPrincesa_*` localStorage keys. Large values use `~p0`, `~p1`, etc.
- A Firestore snapshot listener now applies remote data changes and remote record deletions to localStorage (the Machines key is preserved). When remote data changes, the POS shows a cloud status prompt; tap it to reload the visible screen and display the newly synced data. This avoids silently refreshing while a cashier may be editing a transaction.
- The Android cloud login now requires a server-provisioned `roles/{uid}` document with role `owner` or `staff`. It no longer lets the first person to sign in claim Owner and no longer trusts a cached local role after server errors.
- Firebase project configuration, Email/Password Auth setup, the `(default)` Firestore database, and the manually created Owner role document were confirmed by the project administrator in the Firebase Console. The rules remain undeployed; do not turn on cloud mode until the rules are reviewed, tested, and deployed.

## Required Firebase project configuration

In Firebase Console, open the verified project for La Princesa and register a Web App. Copy only the Web App configuration fields into `firebase-config.js`:

- `apiKey`
- `authDomain`
- `projectId`
- `storageBucket`
- `messagingSenderId`
- `appId`

These are public client configuration values. Do not copy service-account JSON, Admin SDK private keys, private credentials, or passwords into the repository.

Enable:
1. Firebase Authentication — Email/Password.
2. Cloud Firestore — create the database in the correct region.
3. Firestore rules — review the draft in `firestore.rules` before deploying.

## Owner account provisioning

The Owner must sign in using the email address registered for the Owner's own Firebase Authentication account. A full email address is accepted as-is. A username without `@` is converted to `username@laprincesa-pos.app`, so use the full email when the Auth account uses a different domain.

After creating the Owner Auth user, copy its UID and manually create this Firestore document:

- Collection: `roles`
- Document ID: the Owner Auth UID
- Fields:
  - `role`: `owner`
  - `name`: the Owner's real display name (for example, `Sheena Marie Cuizon`)
  - `email`: the same email as the Auth account

The app now prefers `name` for the displayed Owner identity, with `username` retained as a backwards-compatible fallback. Do not create a public client-side first-owner bootstrap. Once the Owner can sign in, the Owner POS can add Staff accounts through its existing account-management workflow.

## Safe first-data migration

Before enabling cloud mode on a live POS:
1. Make an export/backup of the current Android Staff POS data on the device holding the authoritative records.
2. Choose that one Android device as the first migration source.
3. Confirm the Firebase project and Owner account before entering cloud mode.
4. Deploy reviewed Firestore rules and test with non-production data first.
5. Configure the same Firebase Web App values for Android and iOS.
6. Sign in first on the authoritative Android device so its existing `laPrincesa_*` records seed Firestore.
7. Verify the Firestore `data` documents and compare POS totals/orders with the backup.
8. Only then sign in on iOS and confirm it hydrates the same records.
9. Test a new order/payment/inventory update on Android and confirm it appears on iOS; then test an Owner edit on iOS and confirm Android receives it.
10. Test concurrent edits, offline/reconnect, staff permissions, and backup/restore before production release.

Do not let multiple devices perform first-time migration simultaneously. The existing storage sync is document/key based and does not yet provide transaction-level conflict resolution for simultaneous edits to the same POS record.

## Firestore rules draft

The accompanying `firestore.rules` is a starting point, not a verified production ruleset. It grants authenticated Owner/Staff accounts access to the shared `data` collection because the current app stores POS data in that one collection. It protects the `roles` collection so only Owner can manage Staff roles, and the first Owner must be manually provisioned.

Important limitation: because the current data model puts multiple POS settings and business records in the same `data` collection, the rules cannot distinguish every Owner-only field from Staff-editable fields. The app's existing UI role checks still apply. A stronger per-feature authorization model would require a separate schema/backend change and must be planned without breaking Android workflows.

## Owner Clear Data generation barrier

The Android Staff bridge now watches `meta/clear_generation`. The iOS Owner POS publishes one incremented generation and four empty root markers in a single Firestore batch for:

- `laPrincesa_orders_v2` — Sales / Orders
- `laPrincesa_inventory_v2` — Inventory
- `laPrincesa_deleted_orders_v1` — Deleted Records
- `laPrincesa_activity_log_v1` — Activity Log

After the first clear, Android Staff writes to these four operational key families include the current generation. Firestore rules reject writes carrying an older generation (including old offline writes). When a Staff device observes a newer generation, it replaces only those four local keys with empty arrays and clears their pending local bridge writes. Root empty markers remain as the durable barrier; only obsolete chunk documents may be deleted by Owner.

The first generation is compatible with old clients that omit the generation field. Once the generation becomes 1, operational writes without the matching `g` field are rejected. Do not deploy the new rules without updating the Android Staff APK and iOS Owner app together.

The Firestore Emulator tests cover the atomic Owner marker batch, current-generation Staff writes, rejection of stale/untagged writes, and chunk/root deletion permissions. These tests do not replace real-device testing, especially offline/reconnect tests.

## Do not release yet

- Do not merge this development branch to `main` until Firebase config, rules, account provisioning, migration, and cross-device tests pass.
- Do not install or distribute an updated Android APK until separately approved.
- Do not change the printer connection, receipt print sequence, or cash-drawer trigger.
