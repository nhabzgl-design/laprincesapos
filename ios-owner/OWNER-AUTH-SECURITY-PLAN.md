# La Princesa Owner POS — Authentication & Security Plan

Status: repository-source audit and iOS development only. The live Firebase project and deployed Firestore rules have not been verified.

## Intended app roles

- Android: Staff POS. Keep the installed APK, production source, receipt-print sequence, printer bridge, and cash-drawer behavior unchanged.
- iPhone: full Owner POS, not read-only monitoring. Owner may use the Owner-level order, payment, customer, inventory, expense, shift, report, settings, and staff-management functions.
- Both clients must use the exact same verified Firebase project and Firestore data format.

## Current source findings

- Android `main/index.html` has blank Firebase `apiKey` and `projectId` values in checked-in source. A different runtime injection has not been verified.
- The checked-in cloud module uses Firebase Auth and Firestore collection `data`, with document IDs based on the POS `laPrincesa_*` localStorage keys and optional chunk documents.
- The existing cloud module reads the collection during hydration and writes local POS state back to Firestore. The source does not establish a continuous remote snapshot listener.
- No Firestore rules file, Firebase project configuration, or verified deployed rules were found in the repository.
- iOS Owner config is intentionally blank until the actual Android project is confirmed.

## iOS Owner authentication behavior

The iOS Owner role lookup now:
1. Reads `roles/{uid}` from Firestore on login.
2. Requires an existing server-returned role document with `role == "owner"`.
3. Denies access if the role is missing or is not Owner.
4. Does not create the first Owner from the iOS client.
5. Does not use cached local role data as authorization after a failed server check.

## Required database security

Because the iPhone app is full Owner POS, its verified Owner identity needs the intended Owner read/write permissions for the existing POS data and staff-management operations. Staff permissions and existing Android write paths must remain unchanged.

Before any rules change:
- Export and review the currently deployed rules.
- Identify every Firestore path and operation used by Android Staff and iOS Owner.
- Preserve existing Staff permissions.
- Restrict Owner operations to authenticated accounts with a server-side Owner role.
- Prevent Staff from gaining Owner-only account or settings permissions.
- Test allowed Owner operations and denied Staff/unauthenticated operations in a test project or emulator.
- Never deploy a generic ruleset based only on this document.

## Release blockers

- [ ] Confirm the Firebase project ID used by the installed Android POS.
- [ ] Confirm current Android POS records are successfully present in that project's Firestore `data` collection.
- [ ] Confirm the existing Owner Auth UID and `roles/{uid}` record.
- [ ] Review the deployed Firestore rules and preserve Android Staff behavior.
- [ ] Test Owner order, payment, inventory, customer, expense, shift, report, and staff operations.
- [ ] Reconcile totals and deleted/voided records against Android reports.
- [ ] Verify remote-change sync, concurrent edits, reconnect, and stale/offline behavior.
- [ ] Confirm Android source, APK workflow, printer, receipt, and cash drawer remain unchanged.
- [ ] Complete iPhone Safari/PWA testing before a production release.
