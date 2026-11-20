# La Princesa iOS Owner Monitoring — Security & Regression Test Matrix

Status: implementation tests pass in GitHub Actions; live Firebase and physical iPhone tests remain pending.

## Authentication

| Case | Expected result |
|---|---|
| Firebase config missing | Login disabled; dashboard hidden |
| Invalid credentials | Login rejected |
| Staff account signs in | Server role lookup denies access and signs out |
| Missing Owner role document | Deny; no first-Owner bootstrap |
| Existing server role says Owner | Allow monitoring dashboard |
| Role lookup fails/offline | Fail closed; do not trust cached role |
| Owner logs out | Hide dashboard and end authenticated session |

## Monitoring-only behavior

| Case | Expected result |
|---|---|
| New Staff order/payment syncs | Owner dashboard updates through Firestore snapshot listener |
| Inventory or activity changes | Owner view updates without manual refresh |
| Owner opens dashboard | Can read sales, orders, collections, balances, expenses, inventory, customers, and activity |
| Owner tries to create/edit an order | No such control or action exists |
| Owner tries to edit products/inventory or record payment | No such control or action exists |
| Printer / receipt / cash drawer | No controls or hardware bridge in the iOS app |

## Clear Operational Data

| Case | Expected result |
|---|---|
| Staff user attempts Clear Data | Not available; access denied |
| Owner cancels confirmation | No database changes |
| Device is offline | Clear Data is blocked; no database changes |
| Backup is offered | JSON backup includes only the four clearable operational datasets |
| Owner confirms Clear Data | Atomic generation increment and four empty root markers |
| Obsolete chunks exist | Only chunks for the four clearable keys are deleted |
| Customers / Expenses / Services / Machines exist | All remain unchanged |
| Owner/Staff accounts and printer settings exist | All remain unchanged |
| Staff device has stale offline operational writes | Old generation writes are rejected after reconnect |
| New Staff order after clear | Accepted with current generation and visible to Owner |

## Regression / release checks

- Android Staff order creation, payments, inventory deduction, shifts, and normal cloud sync remain functional.
- Gross Sales and Net Sales reconcile with Staff POS using the same order/payment definitions.
- Receipt printing completes once in its existing sequence.
- Cash drawer still triggers only through its existing authorized Staff POS action.
- No Android printer, receipt, cash-drawer, or order-processing code is changed by PR #30.
- Reconnect and stale/offline status are clear.
- Test on a physical iPhone before production release.

## Critical warning

PR #37 contains the matching Staff generation barrier and Firestore rules, but remains a draft. Do not deploy or use synchronized Clear Data in production until those dependencies are reviewed, merged, and deployed to the correct Firebase project with explicit Owner approval.
