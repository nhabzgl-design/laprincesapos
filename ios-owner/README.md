# La Princesa iOS Owner Monitor

Status: PWA preview shell created on development branch; live data integration blocked pending verified cloud configuration.

## Product scope

A separate iPhone-friendly, read-only Owner app for La Princesa POS. The shop Android POS remains the system of record and continues to handle all order creation, edits, voids, payments, inventory changes, staff management, receipts, Bluetooth printing, and cash-drawer actions.

### Owner can view
- Live order list and order status
- Customer directory and customer order history
- Sales, collections, payment-method totals, and unpaid balances
- Inventory and stock levels
- Staff list and activity/logbook
- Daily/monthly reports

### Owner cannot do
- Create, edit, cancel, or void orders
- Record or reverse payments
- Change inventory, products, prices, staff, or settings
- Trigger a receipt print or cash drawer
- Upload or overwrite POS data

No camera, SMS integration, voice/video calling, or paid gateway is in scope.

## Repository audit (main branch, 2026-11-17)

- The Android app is a bundled Android WebView app. Its Java bridge exposes printer and backup functionality. These files must not be changed for the iOS monitoring project.
- The current POS HTML contains a Firebase Auth/Firestore integration, but `window.LP_FIREBASE` has empty `apiKey` and `projectId` values on `main`. Therefore the cloud layer is disabled in this checked-in source.
- When enabled, the existing cloud layer reads and writes documents in Firestore collection `data`, with POS localStorage keys prefixed `laPrincesa_`. It hydrates by one-time `getDocs` calls and does not currently use Firestore `onSnapshot` listeners.
- The existing POS cloud layer writes POS state. It must not be reused as-is by a monitoring client because it is not a read-only interface and the current repository does not include verified Firestore Security Rules.
- The iOS client must not be allowed to write to `data`. Read-only UI restrictions alone are insufficient; Firestore rules must enforce read-only owner access.
- No verified Firebase project configuration or deployed security rules were found in the checked-in `main` source during this audit.

## Current development files

- `index.html`: iPhone-friendly monitoring preview with Overview, Orders, Customers, Reports, Inventory, Staff/Activity sections.
- `manifest.webmanifest`, `sw.js`, and `icon.svg`: installable PWA shell assets.
- The preview intentionally shows no sample sales/customer/order data and has no POS write actions. It is not connected to Firebase and is not a production/live dashboard.

## Safe implementation sequence

1. Confirm the actual Firebase project used by the live Android POS and verify that the shop device is successfully syncing orders to it. Do not ask the user to paste private service-account keys.
2. Confirm the Firestore document schema and the exact owner authentication identity. Do not infer that a Firebase project is active merely because an old Supabase project exists.
3. Add restrictive Firestore Security Rules: the iOS owner identity may read only the required POS data; it may not create, update, or delete any POS data. Staff and shop permissions must remain unchanged.
4. Add a separate iOS-friendly web/PWA client under this directory. Use Firebase Auth and Firestore listeners for real-time read-only display; do not write to the POS data collection.
5. Test using a separate test owner account and test data. Verify denied writes, data visibility, reconnect behavior, and no effect on Android printing/cash-drawer behavior.
6. Only after the PWA is proven stable, assess whether a native iOS wrapper is needed. Native iOS distribution/signing is a separate step and may require Apple developer tooling and account setup.

## Free-tier guardrails

- Keep the Firebase project on the no-cost Spark plan. Do not enable billing or attach a payment method for this project.
- Design around the currently applicable free quotas and handle quota exhaustion as a visible sync-unavailable state, never by silently enabling paid services.
- Do not add camera streaming, TURN servers, paid SMS, paid push services, or other metered providers.
- Verify current Firebase quota terms before release; quotas and product terms can change.

## Release gates

- [ ] Live Android POS cloud sync verified against the correct Firebase project
- [ ] Firestore Security Rules reviewed and tested (read allowed, all owner writes denied)
- [ ] Owner login works with an explicitly provisioned owner account
- [ ] Orders, customers, payments, inventory, reports, and activity views match Android records
- [ ] Real-time listener and reconnect behavior tested
- [ ] Free-tier usage and quota-exhaustion behavior tested
- [ ] Android APK source, printer bridge, receipt sequence, and cash-drawer trigger unchanged
- [ ] User approves any production deployment or merge
