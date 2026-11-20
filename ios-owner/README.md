# La Princesa iOS Owner Monitoring PWA

Status: read-only Owner monitoring PWA under development. This is a separate site from the Android Staff POS.

## Product scope
- Owner-only sign-in verified against the server-side Firestore document `roles/{uid}`.
- Read-only dashboard for orders/sales, collections, outstanding balances, inventory and expenses.
- Live Firestore snapshot listeners on the shared `data` collection and `meta/clear_generation`.
- Owner-only Clear Operational Data with a pre-clear JSON safety backup and generation barrier.
- No order creation, checkout, payment entry, inventory editing, expense entry, staff administration, settings editing, receipt printing, printer bridge, or cash-drawer controls.

## Shared data contract
The PWA reads the existing Firestore `data` documents written by the Staff POS bridge. Main documents contain `v` and optional `parts`; large values use `<key>~pN` chunk documents with `s`. Operational records use the existing `laPrincesa_*` keys. The clear-generation document is `meta/clear_generation`.

## Clear scope
Only these four operational keys are cleared:
- `laPrincesa_orders_v2`
- `laPrincesa_inventory_v2`
- `laPrincesa_deleted_orders_v1`
- `laPrincesa_activity_log_v1`

Customers, expenses, services, machines, accounts, printer settings and all unrelated records are preserved. The Owner PWA first downloads a JSON backup, then writes the generation barrier and four empty operational documents. Chunk cleanup is limited to those four keys.

## Security and release gates
- The public Firebase Web App config is not a secret. Never commit service-account JSON, Admin SDK credentials, private keys or passwords.
- Firestore Security Rules remain the authoritative access control; client-side Owner checks do not replace rules.
- Do not claim production live sync until the Android Staff POS and iPhone Safari are tested against the same live Firestore project.
- Deploy this folder to a separate Netlify site only. Do not replace the Staff POS site.
- No changes are made to the Android Staff POS, receipt-print sequence, printer bridge, or cash-drawer trigger.
