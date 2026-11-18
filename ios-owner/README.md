# La Princesa iOS Owner POS

Status: development PWA; not yet connected to a verified live Firebase project and not production-ready.

## Product scope

A separate iPhone-friendly Owner POS for the shop. It is intended to provide the Owner's full POS access using the same business records as the Android Staff POS. Android remains the Staff POS and its installed APK, source, printer bridge, receipt sequence, and cash-drawer behavior are out of scope for changes.

The iOS Owner app is not a read-only monitor. Owner access is intended to include the POS functions already exposed by the Owner role in the shared app: orders, payments, customers, inventory, services/products, expenses, shifts, reports, settings, and staff-account administration. Access must be granted only to an existing server-verified Owner account.

## Important current limitations

- `ios-owner/firebase-config.js` is intentionally blank. No verified Firebase project ID is available in the checked-in source.
- The Android `main/index.html` also contains blank Firebase `apiKey` and `projectId` values. Repository source alone does not prove that the installed Android app is connected to Firestore.
- The iOS client uses the existing Firestore `data` document format and the same `laPrincesa_*` localStorage keys. Its current cloud module performs an initial one-time Firestore read and uploads local changes. It does not yet have a verified continuous remote snapshot subscription; manual sync is not the same as real-time sync.
- No Firestore rules file or verified deployed rules were found in the repository.
- No Netlify deployment or real iPhone Safari test has been completed.
- iOS browser printing and cash-drawer support must not be assumed to match Android native Bluetooth behavior. Android remains the hardware POS unless iOS hardware compatibility is separately tested.

## Security change in this branch

The iOS Owner role check now requires a server-read `roles/{uid}` document with `role == "owner"`. It no longer creates the first Owner from the iOS client and no longer accepts a cached local role after a failed server check. A missing role or failed server check denies access.

This client-side check is not a replacement for Firestore Security Rules. Rules must preserve existing Android Staff access and permit the verified Owner to perform only the intended Owner operations.

## Safe implementation sequence

1. Verify the exact Firebase project actually used by the installed Android Staff POS and confirm that its current orders are present in Firestore `data`.
2. Verify the existing Owner Firebase Auth account and its server-side `roles/{uid}` document.
3. Inspect and back up the currently deployed Firestore rules before changing anything. Do not deploy guessed rules over a live Staff POS.
4. Confirm the existing Firestore data format, chunk documents, and write permissions. Preserve the Android app's current write paths.
5. Finish and test the iOS Owner POS against a non-production/test dataset, including Owner-only login, CRUD operations, staff administration, and data reconciliation.
6. Add and test safe remote-change synchronization. Avoid claiming real-time sync until remote changes are observed on the iPhone without manual refresh and concurrent edits have been tested.
7. Configure a separate Netlify draft site using branch `ios-owner-monitoring-dev` and publish directory `ios-owner`. Do not replace the Staff POS site.
8. Test on iPhone Safari and install to Home Screen. Confirm offline/stale state is clearly shown.

## Non-negotiable protections

- Do not modify the Android `main` branch or its APK workflow for this iOS work.
- Do not change the printer bridge, receipt-print order, or cash-drawer trigger in Android.
- Do not merge PR #30 or publish a production Netlify site without explicit approval.
- Never put Firebase service-account JSON, Admin SDK credentials, private keys, or user passwords in this repository.
- Never claim that the apps share live data until the actual runtime Firebase project and successful cross-device tests are verified.
