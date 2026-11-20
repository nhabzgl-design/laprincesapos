# La Princesa Owner Monitoring — Data Contract

The iOS Owner PWA reads the same Firestore `data` collection used by the Staff POS bridge. It does not write operational records. The only intentional write action is Owner-only Clear Operational Data.

## Firestore document format
- Main document ID is the matching `laPrincesa_*` storage key.
- Small payloads are stored in `v` as a JSON string.
- Large payloads use `parts` and companion documents named `<key>~p<index>`, whose `s` fields contain text chunks.
- `meta/clear_generation` is the shared generation barrier.
- The Owner PWA listens to both `data` and `meta/clear_generation` with Firestore realtime snapshots.

## Read-only dashboard keys
| Key | Dashboard use |
|---|---|
| `laPrincesa_orders_v2` | Orders, sales, paid/partial/unpaid totals, balances |
| `laPrincesa_deleted_orders_v1` | Exclude deleted/voided orders from active view |
| `laPrincesa_inventory_v2` | Stock levels and low-stock indicators |
| `laPrincesa_expenses_v2` | Expense summaries |
| `laPrincesa_activity_log_v1` | Preserved for audit; not modified by normal monitoring |
| `laPrincesa_customers_v2` | Preserved; not changed by monitoring |
| `laPrincesa_services_v2`, `laPrincesa_machines` | Preserved reference/operations data |

## Financial display rules
- Gross Sales uses order total plus recorded discount, attributed to the order date.
- Net Sales uses the recorded order total, attributed to the order date.
- Collections uses payment entries and their payment timestamps; legacy collected/paid totals fall back to the order date only when no payment entries exist.
- Outstanding Balance is total less recorded collections, bounded at zero.
- These figures must still be reconciled against production records before release. Do not assume the dashboard is financially certified from static code checks alone.

## Exact Clear Data scope
Only `orders_v2`, `inventory_v2`, `deleted_orders_v1`, and `activity_log_v1` are cleared. Customers, Expenses, Services, Machines, accounts, printer settings, and unrelated data are preserved.
