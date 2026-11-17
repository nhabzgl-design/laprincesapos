# Owner POS Security Test Matrix

Status: planned tests; no live Firebase project or deployed rules are available to execute these yet.

## Authentication tests

| Case | Expected result |
|---|---|
| Firebase config missing | Login disabled; dashboard hidden |
| Invalid credentials | Login rejected; dashboard hidden |
| Authenticated staff account | Role lookup from server returns staff; sign out and deny |
| Authenticated account with no role document | Deny; no first-owner bootstrap |
| Authenticated owner account with server-readable `roles/{uid}.role == "owner"` | Allow dashboard shell |
| Role lookup denied, offline, or server unavailable | Fail closed; dashboard hidden |
| Cached local role says owner but server check fails | Deny |

## Data authorization tests (must be run against test project before release)

| Request as Owner identity | Expected result |
|---|---|
| Read approved POS data documents | Allow only after explicit rules approval |
| Create/update/delete POS `data` document | Deny |
| Create/update/delete role document | Deny |
| Write to meta/bootstrap documents | Deny |
| Delete/overwrite cloud data | Deny |
| Use Staff-only mutation endpoints | Deny / not exposed |

## Regression tests

- Verify Android Staff login and normal POS operations are unchanged.
- Verify sales/order writes from Staff still work under the existing intended Staff rules.
- Verify receipt printing still completes once and in the existing order.
- Verify cash drawer pulse remains triggered only by its existing Staff POS action.
- Verify Android APK workflow and artifact production are unchanged.
- Verify iOS Owner logout hides all dashboard content immediately.
- Verify Owner app never writes POS records or changes local Staff POS data.

## Important limitation

Do not deploy newly invented Firestore rules over the current project. Existing Staff permissions and write paths must first be inspected from the actual deployed rules. The correct final rules should preserve Staff operations while restricting Owner to read-only access.
