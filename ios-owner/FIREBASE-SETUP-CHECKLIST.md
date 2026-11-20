# La Princesa Owner Monitoring — Firebase Checklist

## Current client configuration
`ios-owner/firebase-config.js` is populated for Firebase project `laprincesa-pos`. These are public Firebase Web App identifiers, not Admin credentials. The PWA must continue using the same project as the Staff POS; do not create a separate database.

The user has confirmed that the production Firebase setup and rules have already been completed. Do not ask them to repeat setup or redeploy rules without a concrete runtime error. A repository configuration alone does not prove a live sign-in or cross-device read has succeeded.

## Authentication
- Firebase Auth signs in the Owner.
- The PWA fetches `roles/{uid}` from the server using `getDocFromServer`.
- Only an existing role document with `role == "owner"` is accepted.
- Missing role, Staff role, or failed server verification denies access.
- Firebase Auth persistence restores the signed-in session after refresh; Owner role is re-verified.

## Shared database
- Firestore collection: `data`.
- Main document IDs match the Staff POS `laPrincesa_*` keys.
- Large values use companion chunk documents `<key>~p<index>`.
- Realtime listeners observe `data` and `meta/clear_generation`.

## Before production release
1. Sign in using the already-provisioned Owner account.
2. Confirm the live data listener reads the existing Staff POS records.
3. Add a harmless test sale on a Staff device and confirm it appears on iPhone without refresh.
4. Reconcile Gross Sales, Net Sales, Collections, Outstanding Balance, Expenses and Inventory against the Staff POS.
5. Test Clear Operational Data only with explicit approval and all affected Staff devices accounted for.

Never place service-account JSON, Admin SDK credentials, private keys, or passwords in this repository. Firestore Security Rules remain authoritative; client-side checks do not replace them.
