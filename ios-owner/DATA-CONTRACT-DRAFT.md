# La Princesa Owner POS — Data Contract Draft

Status: discovery notes only; not a production schema and not connected to a live database.

## Purpose

Map the existing Android Staff POS local data to the future shared cloud read model for the iOS Owner POS. This file does not change Android code, permissions, printing, receipt flow, or cash-drawer behavior.

## Storage keys observed in the checked-in Android `main/index.html`

| Local key | Owner view it can support | Notes / cautions |
|---|---|---|
| `laPrincesa_orders_v2` | Orders, sales, unpaid balances, payment summaries | Active orders are filtered against deletion markers. |
| `laPrincesa_deleted_orders_v1` | Voided/deleted order audit | Must be retained for audit; do not count voided orders as active sales. |
| `laPrincesa_customers_v2` | Customer directory and order history | Join to orders only after confirming stable customer identifiers. |
| `laPrincesa_services_v2` | Service/product labels in order details | Read-only reference data. |
| `laPrincesa_inventory_v2` | Inventory and stock levels | Need verify stock movement/deduction semantics before calculating stock history. |
| `laPrincesa_package_recipes_v1` | Package/recipe context | Do not infer stock deductions without confirming recipe rules. |
| `laPrincesa_expenses_v2` | Expense summaries and net figures | Verify categories, dates, and whether entries are reversed/voided. |
| `laPrincesa_activity_log_v1` | Staff activity and audit log | Preserve actor, timestamp, action, and reason fields where present. |
| `laPrincesa_shift_v1` | Current shift status | Includes shift metadata; verify fields before displaying. |
| `laPrincesa_shift_cash_v1` | Shift cash summary | Do not equate this automatically with total sales. |
| `laPrincesa_shift_movements_v1` | Cash-in/out movement view | Keep separate from sales and collections. |
| `laPrincesa_branches` | Branch filter | Confirm whether multiple branches are actively used. |
| `laPrincesa_machines` | Machine/operations monitoring | Optional Owner view if this data is operationally relevant. |

## Financial interpretation already visible in Android source

- An order's total is read from `total`.
- Collected amount prefers `collected`; older records may fall back to `paid`.
- Collected amount is clamped between zero and order total.
- Balance is total minus collected.
- Status is Paid, Unpaid, or Partial based on the computed balance and collection.
- Payment breakdown should use individual `payments[]` entries when available; legacy orders may only have `payMethod`, `paid`/`collected`, and `date`.
- Deleted/voided order records must be excluded from active sales totals and remain visible only in audit views.

These are implementation observations, not a complete financial specification. Refund handling, discounts, multi-payment edge cases, date/time zones, and report cut-off rules still need full validation before production totals are shown.

## Proposed shared-cloud read model

The Owner client should subscribe only to normalized, server-authorized read data. Suggested logical groups:

1. `orders` and `order_payments`
2. `customers`
3. `services`
4. `inventory` and (if supported) `inventory_movements`
5. `expenses`
6. `activity_log` and `deleted_orders`
7. `shifts` and `shift_movements`
8. `branches` and `machines`

This is a proposal, not an instruction to create these collections yet. The final cloud schema must be based on the verified runtime backend and tested against real Android records.

## Security requirements

- Owner iOS identity: read-only access to the required business data.
- Owner identity must be denied create, update, and delete operations at the database rules layer.
- Staff Android permissions and existing workflows must remain unchanged.
- Never put Firebase Admin credentials, service-account JSON, or privileged secrets in the iOS/PWA client.
- Do not reuse the Android cloud-sync write module as the Owner data layer.

## Release blockers

- Runtime cloud project/configuration and current Android sync path are not verified from repository source.
- Exact field schemas and identity relationships need confirmation.
- Database rules must be reviewed and tested, including explicit denied-write tests.
- Live totals must be reconciled against Android POS reports before Owner release.
- No production deployment or merge without Owner approval.
