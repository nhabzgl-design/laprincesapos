# La Princesa Owner Monitoring — Security & Regression Test Matrix

## Authentication
| Case | Expected result |
|---|---|
| Firebase config missing or wrong project | No dashboard; clear configuration error |
| Invalid credentials | Login rejected |
| Staff account signs in | Server role lookup returns Staff; sign out and deny |
| Authenticated user has no role document | Deny |
| Server role document says Owner | Allow read-only dashboard |
| Role lookup fails or is offline | Fail closed; do not trust cached role |
| Page refresh with valid Owner session | Restore Auth session and re-verify Owner role |
| Logout | Stop listeners, hide dashboard, sign out |

## Read-only monitoring
| Case | Expected result |
|---|---|
| Staff creates or updates an order | iPhone dashboard updates without manual refresh |
| Payment is recorded | Collections and outstanding balance update |
| Inventory changes | Stock and low-stock indicators update |
| Expense is recorded | Expense view updates |
| Network disconnects | Show stale/offline state; do not claim live sync |
| Network reconnects | Firestore snapshots restore current data |
| Unknown or incomplete chunk document | Do not parse partial JSON as a valid record |

## Clear Operational Data
| Case | Expected result |
|---|---|
| Non-Owner or logged-out user triggers clear | Deny; no writes |
| Owner is offline or listener is not live | Deny; no writes |
| Owner confirms clear | Download JSON backup first |
| Clear generation commits | Clear only orders, inventory, deleted orders, activity log |
| Customer/expense/service/machine/account/printer data | Remains unchanged |
| Staff device reconnects with older generation | Stale operational data must not reappear |

## Regression protections
- Android Staff can still sign in and complete normal order/payment/inventory workflows.
- Receipt printing still completes once and in its existing sequence.
- Cash drawer still triggers only through its existing authorized Staff POS action.
- No changes to Android printer bridge, receipt flow or cash-drawer trigger are included in this iOS PR.
- Production sync and accounting must be verified against real records; static checks and emulator tests alone are insufficient.
