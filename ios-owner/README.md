# La Princesa iOS Owner Monitoring

Status: development PWA on `ios-owner-monitoring-dev`. The app code now targets the same Firebase project ID as the Staff POS, but production login, live data reconciliation, and physical iPhone testing are still pending.

## Exact product scope

The iOS app is **Owner Monitoring only**. It is not a second POS.

- Read-only view of Staff POS sales, orders, collections, outstanding balances, expenses, inventory, customers, and activity.
- Live Firestore listener (`onSnapshot`) on the existing `data` collection.
- Server-verified Owner login through `roles/{uid}`; Staff accounts are denied.
- The only action that writes to the shared POS database is the Owner-only **Clear Operational Data** action.
- No order entry, checkout, product editing, payment entry, inventory editing, printer, receipt, or cash-drawer controls.

## Shared data source

`ios-owner/firebase-config.js` uses the existing Staff POS Firebase Web App project ID `laprincesa-pos`. The dashboard reads the existing `laPrincesa_*` documents from Firestore `data`, including chunk documents named `<key>~p<index>`.

A configured project ID does not by itself prove that the live Owner account can authenticate or that the installed Staff device has current records in this project. Those checks must be completed before release.

## Owner-only Clear Operational Data

The action clears only:
- Sales / Orders: `laPrincesa_orders_v2`
- Deleted Records: `laPrincesa_deleted_orders_v1`
- Inventory: `laPrincesa_inventory_v2`
- Activity Log: `laPrincesa_activity_log_v1`

It first offers a JSON safety backup, then atomically increments `meta/clear_generation` and writes empty root markers for those four keys. It removes only obsolete chunks belonging to those keys.

It preserves Customers, Expenses, Services, Machines, Owner/Staff accounts, printer settings, and all unrelated documents.

## Required dependency

PR #37 (`lp-safe-clear-scope-repair`) contains the matching Android Staff generation-barrier handling and Firestore rules. It is still a separate draft. The synchronized Clear Data action must not be used in production until that change is reviewed, merged, and its rules are deployed to the same verified Firebase project with explicit Owner approval.

## Validation and remaining release checks

- Current iOS Owner PWA workflow: syntax, manifest, monitoring-only static checks, and Owner financial helper tests passed.
- Not yet verified on a physical iPhone.
- Not yet verified with the actual production Owner login and live Staff records.
- Reconcile Gross Sales, Net Sales, collections, unpaid balance, expenses, orders, and inventory against the Staff POS.
- Test Clear Data with every Staff device online, offline, and reconnecting.
- Verify Firestore rules preserve normal Staff order/payment/inventory writes and reject stale offline operational writes.
- Do not merge PR #30, publish a production Netlify site, or deploy Firestore rules without explicit release approval.

## Non-negotiable protections

- Do not alter Android Staff order processing, receipt printing, printer bridge, or cash-drawer trigger as part of iOS work.
- Do not delete Customers, Expenses, Services, Machines, accounts, printer settings, or unrelated data during Clear Data.
- Never place Firebase Admin credentials, service-account JSON, private keys, or user passwords in the client.
