# La Princesa Owner POS — Firebase Setup Checklist

Status: client-side read-only listener prepared; no live Firebase project has been connected or changed.

## Important architecture decision

The Owner app must use the exact same Firebase project as the Android Staff POS. The checked-in Android source uses Firebase Authentication and Cloud Firestore collection `data`. POS values are stored as documents whose IDs are the localStorage keys (for example `laPrincesa_orders_v2`); large values may be split into companion documents named `<key>~p<index>`. The Owner app reads this existing format and does not write to it.

Do not create a second unrelated Firebase project for the Owner app. That would produce a separate database and the iPhone would not see the Android POS records.

## Before creating or changing anything

1. Open the Firebase Console and identify the project that the installed Android Staff POS actually uses.
2. Compare its project ID with the Android app's runtime Firebase configuration. The checked-in repository currently has blank `apiKey` and `projectId`; the real runtime configuration must be verified from the actual deployment/device or Firebase Console.
3. In Firestore Data, check whether the `data` collection already contains POS documents. Do not delete, rename, or migrate them.
4. In Authentication, check whether the Owner account already exists.
5. In Firestore Rules, copy/export the currently deployed rules before editing. The Staff POS relies on existing write permissions; replacing those rules with a generic Owner-only ruleset can stop orders, payments, inventory sync, staff functions, and other shop operations.

## Firebase Web App config

After the correct project is verified, register a Web App in that same project (if one does not already exist) and copy its Firebase web config values into `ios-owner/firebase-config.js`:

- `apiKey`
- `authDomain`
- `projectId`
- `storageBucket`
- `messagingSenderId`
- `appId`

These are public client configuration values, not Admin credentials. Never place service-account JSON, private keys, Admin SDK credentials, or user passwords in this repository.

Keep `window.LP_OWNER_FIREBASE_CONFIG = null` until the project ID has been confirmed. Do not guess the project ID.

## Owner authentication and role

The Owner client signs in with Firebase Authentication, then reads `roles/{uid}` directly from the server and requires `role == "owner"`. It does not create an owner, modify role documents, or trust a cached role.

If the Owner role document already exists, record the UID and verify its role. If it does not exist, provision the Owner role using a secure administrative process in the correct project; never enable first-owner bootstrap from the iPhone app. Do not share account passwords in chat.

## Firestore access rules — do not deploy a guessed ruleset

The Owner app needs:
- read access to `roles/{uid}` for its own authenticated UID so it can verify the Owner role;
- read-only access to the POS `data` collection for a verified Owner;
- no create, update, or delete access for Owner to POS data, role documents, or bootstrap metadata.

The exact final rules must preserve every existing Staff POS permission and write path. First inspect the deployed rules and test a copy in a Firebase test project or Emulator. Test Staff order/payment/inventory writes and Owner reads/denied writes before deploying any change.

## Netlify deployment

For the Owner PWA site, connect this GitHub repository and select branch `ios-owner-monitoring-dev`. Set:
- Base directory: blank (repository root)
- Build command: blank
- Publish directory: `ios-owner`

Use a Netlify draft/branch deploy for testing. Do not change the production domain or publish the development branch as production until the Owner app has passed login, data reconciliation, security, and offline tests.

## Current client behavior

- Firebase configuration remains intentionally blank.
- The dashboard is hidden until server-side Owner role verification succeeds.
- After Owner verification, the client subscribes to Firestore `data` with a read-only snapshot listener.
- The client parses the Android POS document/chunk format and displays orders, customers, unpaid balances, inventory, activity, expenses, shifts, machines, and basic sales/payment summaries.
- If login, role verification, or data access fails, access is denied or the connection is shown as unavailable. No sample sales are displayed.

## Required tests before release

- Confirm Android Staff POS and Owner app point to the same Firebase project ID.
- Confirm the Android device has successfully written current POS records to Firestore `data`.
- Verify Owner login and server role lookup.
- Verify Owner can read the approved data but cannot write/delete POS documents.
- Reconcile order count, gross, collections, unpaid balance, and payment breakdown against Android reports.
- Check voided/deleted orders, partial payments, date boundaries, and timezone handling.
- Confirm the Staff POS can still create orders, record payments, deduct stock, manage shifts, and sync.
- Confirm Android receipt printing and cash drawer behavior remain unchanged.
- Confirm the listener reconnects and the app clearly indicates offline/unavailable state.

No production Firebase rules, database data, Android source, or APK workflow were changed by this setup guide.
