# La Princesa iOS Owner Monitoring — Authentication & Security Plan

Status: development implementation only. The client is configured for Firebase project `laprincesa-pos`; production authentication, live data access, and deployed Firestore rules have not yet been verified.

## App roles

- Android: Staff POS. Its order processing, stock deduction, receipt-print sequence, printer bridge, and cash-drawer behavior are outside the iOS app scope.
- iPhone: separate Owner Monitoring app. It reads Staff POS data and does not create orders, record payments, edit products, edit inventory, manage staff, print receipts, or operate hardware.
- The sole write action in the iOS app is Owner-only Clear Operational Data for the four approved operational data keys.

## Authentication

1. Sign in with Firebase Authentication.
2. Fetch `roles/{uid}` from the server.
3. Require an existing document with `role == "owner"`.
4. Deny Staff, missing roles, and failed server role checks.
5. Do not create Owner roles or bootstrap the first Owner from the iOS client.

## Shared data and realtime monitoring

- Firebase project ID configured in the iOS client: `laprincesa-pos`.
- Existing Firestore collection: `data`.
- POS document IDs are existing `laPrincesa_*` localStorage keys; large values may use `<key>~p<index>` chunks.
- The dashboard subscribes with Firestore `onSnapshot`; it does not upload local POS state or write ordinary business records.
- The dashboard is hidden until server-side Owner role verification succeeds.

## Owner Clear Operational Data

The only iOS write action targets:
- `laPrincesa_orders_v2`
- `laPrincesa_deleted_orders_v1`
- `laPrincesa_inventory_v2`
- `laPrincesa_activity_log_v1`

The app offers a JSON safety backup, then atomically increments `meta/clear_generation` and writes four empty root markers tagged with the new generation. It removes only obsolete chunks belonging to those operational keys.

The following must remain untouched: Customers, Expenses, Services, Machines, Owner/Staff accounts, printer settings, and unrelated data.

## Required rules behavior

The Firestore rules must:
- Allow a verified Owner to read the existing POS data and read their own role document.
- Preserve the existing Staff POS permissions and write paths.
- Allow only a verified Owner to increment `meta/clear_generation` and publish the four exact empty operational root markers.
- Allow only a verified Owner to delete chunks belonging to those four operational keys.
- Reject stale/untagged Staff operational writes after the generation barrier is active.
- Never allow Clear Data to delete preserved records or unrelated documents.

PR #37 contains the matching draft rules and Staff generation-barrier code. It remains unmerged. Do not use the production Clear Data action until PR #37 is reviewed/merged and the rules are deployed to the verified production project with explicit approval.

## Release blockers

- [ ] Verify the production Firebase project and current Staff POS data in Firestore `data`.
- [ ] Verify the existing Owner Auth account and its server-side `roles/{uid}` document.
- [ ] Review deployed Firestore rules and compare them with PR #37.
- [ ] Run Firestore Emulator tests for Owner clear, Staff writes, stale writes, and preserved data.
- [ ] Reconcile dashboard figures against the live Staff POS.
- [ ] Test real-time updates, offline/reconnect, and multi-device Clear Data.
- [ ] Test on physical iPhone Safari/PWA.
- [ ] Keep Android receipt, printer, and cash-drawer code unchanged.
