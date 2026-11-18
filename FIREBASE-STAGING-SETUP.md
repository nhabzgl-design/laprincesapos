# Firebase Staging Setup — La Princesa POS

This guide is for the `owner-monitoring-staging` branch only. Do not use the production Firebase project for these tests.

## 1. Create a separate Firebase project

1. Open https://console.firebase.google.com/ and choose **Add project**.
2. Use a clear name such as **La Princesa POS Staging**. The Firebase project ID must be unique; Firebase will suggest one.
3. Google Analytics is optional for this test project. It can be disabled.
4. Create the project. Confirm the project selector shows the staging project before doing any further setup.

## 2. Register a Web App and configure this branch

1. In the staging project overview, choose **Add app → Web**.
2. Give it a name such as `La Princesa POS Staging Web`. Hosting is not required for this registration.
3. Copy the Firebase Web App configuration values from the Firebase console.
4. In this branch only, replace the empty values in `firebase-config.js`:
   - `apiKey`
   - `authDomain`
   - `projectId`
   - `storageBucket`
   - `messagingSenderId`
   - `appId`

These are public web-app identifiers. Do not paste a service-account JSON file, Admin SDK private key, password, or any other secret into the repository or chat.

## 3. Enable Authentication

1. Open **Build → Authentication → Get started**.
2. Open **Sign-in method**.
3. Enable **Email/Password**.
4. In **Users**, create a staging Owner test account for Sheena and a separate staging Staff test account. Use test passwords that are not used by production accounts.

## 4. Create the staging Firestore database

1. Open **Build → Firestore Database → Create database**.
2. Choose production mode (not test mode).
3. Select **asia-southeast1 (Singapore)** to match the intended deployment region.
4. Create the database.
5. Open the **Rules** tab and publish the contents of this branch's `firestore.rules` to the staging project only.

The staging rules permit Owner reads of shared POS records but deny Owner create/update/delete on the operational `data` collection. Staff accounts can perform operational writes. Only Owner can manage Staff role documents. Do not publish the permissive rule `allow read, write: if request.auth != null`.

## 5. Provision the two role documents

In **Firestore → Data**, create a collection named `roles`.

For each staging Auth account, create a document whose document ID is the exact Firebase Authentication UID:

**Owner role document**
- Document ID: the staging Owner Auth UID
- `role` — string — `owner`
- `name` — string — `Sheena Marie Cuizon`
- `email` — string — exactly the email used by the staging Owner Auth account

**Staff role document**
- Document ID: the staging Staff Auth UID
- `role` — string — `staff`
- `name` — string — a test cashier name
- `email` — string — exactly the email used by the staging Staff Auth account

Do not reuse production UIDs. Do not copy production role documents.

## 6. Safe test sequence

1. Use only test data in the staging project. Do not import live customer, sales, inventory, or expense data.
2. Sign in to the Android Staff POS with the staging Staff account.
3. Add a clearly marked test product/order/customer. Confirm the Firestore `data` documents are created.
4. Sign in to `owner.html` on the iPhone with the staging Owner account. Confirm the monitoring view receives the shared records.
5. Confirm the Owner account cannot write to the `data` collection (Firestore rules deny it).
6. Confirm the Staff account can perform the intended test sale and inventory changes.
7. Test refresh, sign-out, sign-in, and live updates.

## Safety boundaries

- This branch's `firebase-config.js` is intentionally blank until the staging project is created.
- The production Firebase project, production Firestore rules, `main` branch, and live shop data must remain untouched.
- The Owner Monitor is a read-only dashboard. Its displayed totals are still subject to a separate reconciliation test against actual POS test records before being relied on.
- A green GitHub syntax/CI check does not prove that Firebase authentication, Firestore permissions, or live synchronization work. Those require the staging project and real test accounts.
- Do not deploy the staging rules to production. Do not merge this branch into `main` until the staging tests and security review pass.
