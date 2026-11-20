# La Princesa Owner Monitoring — Firebase Setup Checklist

Status: iOS client configuration now points to the Staff POS project ID `laprincesa-pos`. No live login, production data reconciliation, rules deployment, or database mutation has been performed by this change.

## Shared project and data format

The iOS Owner app must use the same Firebase project as the Android Staff POS. The current iOS Web App config identifies project ID `laprincesa-pos`.

The Staff POS uses Firebase Authentication and Cloud Firestore collection `data`. POS values are stored as documents whose IDs are localStorage keys (for example `laPrincesa_orders_v2`); large values may be split into companion documents named `<key>~p<index>`.

Do not create a second unrelated Firebase project. Do not change, delete, rename, or migrate live POS records.

## Verify before release

1. In Firebase Console, verify project ID `laprincesa-pos` is the project used by the installed Staff POS.
2. Confirm current Staff POS records are present in Firestore `data`.
3. Confirm the existing Owner Authentication account and its `roles/{uid}` document have `role == "owner"`.
4. Export the currently deployed Firestore rules before changing anything.
5. Compare deployed rules with the draft in PR #37 and verify Staff order/payment/inventory writes remain allowed.
6. Run Emulator tests before deploying any rules changes.

## Client configuration

`ios-owner/firebase-config.js` contains the public Firebase Web App config for `laprincesa-pos`. These values identify the client project and are not Admin credentials. Never place service-account JSON, Admin SDK credentials, private keys, or user passwords in this repository.

## Owner authentication and access

The Owner client signs in with Firebase Authentication and fetches `roles/{uid}` from the server. It requires `role == "owner"`. It does not create Owner accounts, modify role documents, or trust cached roles.

The Owner dashboard uses a Firestore snapshot listener on `data`. It does not write normal POS records. Its only write action is the Owner-only Clear Operational Data function.

## Clear Operational Data rules dependency

The iOS action clears only:
- `laPrincesa_orders_v2`
- `laPrincesa_deleted_orders_v1`
- `laPrincesa_inventory_v2`
- `laPrincesa_activity_log_v1`

It increments `meta/clear_generation`, writes empty root markers for those four keys, and deletes only their obsolete chunk documents. Customers, Expenses, Services, Machines, accounts, printer settings, and unrelated records are preserved.

PR #37 contains the matching Staff-side generation barrier and Firestore rules. It is still a draft and has not been merged or deployed. Do not use Clear Data in production until PR #37 is reviewed/merged and the rules are deployed to the same verified project with explicit approval.

## Separate iOS PWA deployment

Use a separate Netlify site:
- Branch: `ios-owner-monitoring-dev`
- Base directory: blank
- Build command: blank
- Publish directory: `ios-owner`

Use a draft/branch deploy only. Do not replace the Staff POS site or publish a production Owner URL until login, live data, financial reconciliation, Clear Data dependencies, security, and physical iPhone tests pass.

## Required live tests

- Verify Owner login and server role lookup.
- Verify live Staff updates arrive on the iPhone without manual refresh.
- Reconcile order count, Gross Sales, Net Sales, collections, outstanding balance, and expenses against Staff POS.
- Check voided/deleted orders, partial payments, date boundaries, and Asia/Manila timezone handling.
- Test Clear Data with Staff devices online, offline, and reconnecting.
- Confirm preserved customer, expense, service, machine, account, and printer settings records remain unchanged.
- Confirm the Staff POS can still create orders, record payments, deduct stock, and sync.
- Confirm receipt printing and cash drawer behavior remain unchanged.

No production Firestore rules, live database records, or Android Staff POS code were changed by this setup.
