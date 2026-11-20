# La Princesa Owner Monitoring — Authentication & Security

## App roles
- Android remains the Staff POS, including its existing receipt-print sequence, printer bridge and cash-drawer trigger.
- iOS is an Owner-only, read-only monitoring PWA. It is not a second checkout/POS terminal.

## Authentication gate
1. Sign in through Firebase Authentication.
2. Fetch `roles/{uid}` from the Firestore server.
3. Require an existing document with `role == "owner"`.
4. Deny Staff, missing roles, and failed server lookups.
5. Restore Firebase Auth persistence after refresh, then re-check the server-side Owner role.

## iOS permissions and UI scope
The iOS app can read the shared monitoring data and display orders/sales, collections, outstanding balances, inventory and expenses. It must not expose order creation, checkout, payment entry, inventory editing, expense entry, staff administration, settings editing, printing, printer or cash-drawer functions.

The only intentional write action is the Owner-only Clear Operational Data function. It writes the generation barrier and empty values for exactly four operational keys, after a downloadable safety backup and explicit confirmation.

## Data protection
Clear scope:
- `laPrincesa_orders_v2`
- `laPrincesa_inventory_v2`
- `laPrincesa_deleted_orders_v1`
- `laPrincesa_activity_log_v1`

Preserve Customers, Expenses, Services, Machines, accounts, printer settings, and all unrelated records.

Firestore Security Rules are authoritative. The client UI being read-only does not itself prevent a user with broader Firestore write permissions from bypassing the UI. Confirm that deployed rules allow the intended Owner reads and the narrowly scoped clear operation while preserving Staff writes. Do not replace live rules with guessed rules.

## Release checks
- Confirm the existing Owner account can sign in and server role verification succeeds.
- Confirm realtime Staff-to-iPhone updates without manual refresh.
- Reconcile financial totals and inventory against live Staff POS records.
- Test the generation barrier and stale/offline device behavior safely.
- Keep the iOS PWA on a separate Netlify site; do not replace the Staff POS site.
