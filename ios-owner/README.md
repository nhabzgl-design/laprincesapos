# La Princesa iOS Owner Monitor

Status: PWA Owner client with secure server-verified login and a read-only Firestore listener prepared on the development branch. Live use remains blocked until the actual Staff POS Firebase project, Owner role, and deployed rules are verified.

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

## Repository audit (2026-11-17)

Cross-checked the checked-in POS `index.html` on `main`, `native-apk-fix`, `stable/apk-235-baseline`, and `fix/cash-drawer-before-receipt`.

- All four checked-in versions contain the Firebase Auth/Firestore cloud module, but `window.LP_FIREBASE` has empty `apiKey` and `projectId` values. In these source versions, the cloud module therefore stays disabled unless a different build/deployment injects configuration (no such injection was verified in the repository).
- When enabled, the cloud module reads and writes documents in Firestore collection `data`, using POS localStorage keys prefixed `laPrincesa_`.
- Initial hydration uses `getDocs(collection(db,'data'))`; the module does not register Firestore `onSnapshot` listeners. Its normal localStorage write interception uploads changes after a short debounce, but that is not a live remote-change subscription. The manual “Mag-sync ngayon” action performs another one-time hydrate.
- The cloud module is not read-only: it uploads local POS state, has a cloud wipe path, and the role bootstrap can create the first owner role. Do not reuse this module as the iOS Owner client's data-access layer.
- No Firestore Security Rules, Firebase project config file, or verified deployed rules were found in the checked-in repository. The real runtime Firebase project/configuration remains unverified.
- Conclusion: repository source alone does not establish that the installed Android POS is currently syncing to a shared cloud database. Do not display live data or claim cross-device real-time sync until the actual runtime backend is verified.
- Do not modify the Android POS, APK source, printer bridge, receipt-print sequence, or cash-drawer behavior as part of the iOS monitor work.

## Current development files

- `index.html`: iPhone-friendly Owner client with server-verified login, logout, and a read-only Firestore snapshot listener for the Android POS `data` collection. It maps the known localStorage-key document format to Owner monitoring views.
- `firebase-config.js`: intentionally blank until the exact Android Staff POS Firebase project is confirmed.
- `FIREBASE-SETUP-CHECKLIST.md`: safe shared-project, Auth, Firestore rules, and testing steps.
- `NETLIFY-DEPLOY-CHECKLIST.md`: isolated Netlify draft deployment settings.
- `manifest.webmanifest`, `sw.js`, and `icon.svg`: installable PWA shell assets.
- The client intentionally shows no sample sales/customer/order data and has no POS write actions. It will not connect until the real Firebase web config is populated. Once connected, it reads Firestore `data` through a snapshot listener; it never writes POS data. This is still a development build, not a production/live service.

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
