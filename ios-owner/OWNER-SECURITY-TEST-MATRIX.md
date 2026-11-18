# La Princesa Owner POS — Security & Regression Test Matrix

Status: planned tests; no live Firebase project or deployed Firestore rules have been verified.

## Authentication tests

| Case | Expected result |
|---|---|
| Firebase config missing | Login disabled; dashboard hidden |
| Invalid credentials | Login rejected |
| Staff account signs in to iOS Owner | Server role lookup returns staff; sign out and deny |
| Authenticated account has no role document | Deny; no first-Owner bootstrap |
| Existing server role document says Owner | Allow Owner POS |
| Role lookup denied, offline, or server unavailable | Fail closed; do not trust cached role |
| User logs out | Hide Owner dashboard and clear authenticated session |

## Owner permissions (test against a non-production database first)

| Operation | Expected result |
|---|---|
| Read POS records | Allow verified Owner |
| Create/edit/cancel orders | Allow verified Owner, subject to existing POS business rules |
| Record/reverse payments | Allow verified Owner, with audit trail and correct balance calculations |
| Edit products/services and inventory | Allow verified Owner; stock calculations must reconcile |
| Manage customers, expenses, and shifts | Allow verified Owner |
| View reports and activity log | Allow verified Owner |
| Add/remove Staff accounts | Allow verified Owner through the supported Auth/role flow |
| Change Owner password | Allow only after reauthentication |
| Access as Staff or unauthenticated user | Deny Owner-only functions |

## Regression tests

- Android Staff can still sign in and perform normal order/payment/inventory workflows.
- Android Staff Firestore writes remain allowed under the existing intended rules.
- Receipt printing still completes once and in the existing sequence.
- Cash drawer still triggers only through its existing authorized Staff POS action.
- Android APK source and build workflow are unchanged.
- iOS Owner edits sync to the same verified records without duplicate or lost transactions.
- Concurrent Android/iOS edits are tested for conflict and data-loss behavior.
- iOS reconnect and offline/stale status are clear.

## Critical warning

Do not deploy newly invented Firestore rules over the live project. First inspect and back up deployed rules and test the complete Staff/Owner permission matrix in a non-production environment.
