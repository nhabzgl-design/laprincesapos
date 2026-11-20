# La Princesa Owner Monitoring — Data Contract

Status: implementation mapping based on the existing Staff POS Firestore document format. Live record reconciliation is still required.

## Purpose

The iOS app is a separate Owner Monitoring client. It reads the Staff POS data from the existing Firestore `data` collection and does not write ordinary POS records.

## Firestore storage format

- Collection: `data`
- Root document IDs: existing `laPrincesa_*` localStorage keys
- Large values: companion chunk docs named `<key>~p<index>`, each containing a string segment in field `s`
- Operational generation barrier: `meta/clear_generation` and the `g` field on the four clearable operational root docs

## Keys used by the Owner dashboard

| Key | Owner view | Clear Data behavior |
|---|---|---|
| `laPrincesa_orders_v2` | Orders, sales, collections, unpaid balances | Clear |
| `laPrincesa_deleted_orders_v1` | Deleted/voided order audit | Clear |
| `laPrincesa_inventory_v2` | Inventory and stock levels | Clear |
| `laPrincesa_activity_log_v1` | Staff activity log | Clear |
| `laPrincesa_customers_v2` | Customer count / records | Preserve |
| `laPrincesa_expenses_v2` | Expense summaries | Preserve |
| `laPrincesa_services_v2` | Service/product reference data | Preserve |
| `laPrincesa_machines` | Machine/operations records | Preserve |

Other keys, including shifts, branches, recipes, and settings, are not deleted by the Owner Clear Data function.

## Financial interpretation

The Owner dashboard mirrors the Staff POS financial helper semantics:
- Order total is read from `total`.
- Collected amount prefers `collected`; older records may fall back to `paid`.
- Collected amount is clamped between zero and order total.
- Balance is total minus collected.
- Status is Paid, Unpaid, or Partial based on the computed balance and collection.
- Gross Sales adds back recorded discounts to the order total.
- Net Sales is Gross Sales less discounts and refunds.
- Collections use individual `payments[]` timestamps when present; legacy orders fall back to the order date and collected/paid amount.
- Orders with `deletedAt` or an ID present in the deleted-record archive are excluded from active sales totals.

These are implementation semantics, not a substitute for reconciling real totals against the Staff POS. Refunds, discounts, partial payments, date boundaries, and Asia/Manila timezone handling must be tested against live records.

## Security and Clear Data

- Server-side Owner role verification is required before displaying the dashboard.
- The app is read-only for normal business data.
- The only write action is Owner-only Clear Operational Data.
- Clear Data increments the shared generation and writes four empty root markers atomically.
- Only chunks belonging to the four clearable keys may be deleted.
- Customers, Expenses, Services, Machines, Owner/Staff accounts, printer settings, and unrelated records must remain unchanged.
- The Staff-side generation barrier and Firestore rules are in PR #37 and must be reviewed/merged/deployed before synchronized Clear Data is used in production.

## Release blockers

- Verify the runtime Firebase project and current Android sync path.
- Reconcile live data and financial totals against the Staff POS.
- Verify deployed Firestore rules and denied-write behavior.
- Test online/offline/reconnect and multi-device Clear Data.
- Complete physical iPhone Safari/PWA testing.
