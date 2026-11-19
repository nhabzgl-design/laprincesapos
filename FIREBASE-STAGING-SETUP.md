# Firebase Staging Setup — La Princesa POS

This guide applies only to the `owner-monitoring-staging` branch and the Firebase project ID `la-princesa-pos-staging`. Never use the production Firebase project for these steps.

## Current repository configuration

- `firebase-config.js` is already populated and points to `la-princesa-pos-staging`.
- `firebase.json` maps Firestore rules to `firestore.rules`.
- The staging rules are tested in the Firestore Emulator CI workflow.
- The rules are **not considered deployed** until the manual deployment workflow completes successfully and the Firebase Console shows the same rules.

## 1. Verify the staging Firebase project

1. Open [Firebase Console](https://console.firebase.google.com/).
2. In the project selector, find the project whose exact project ID is `la-princesa-pos-staging`.
3. If it is missing, stop. Do not create a project with a guessed ID and do not select the production project.
4. Open **Build → Firestore Database**. Confirm the default database exists. If it does not exist, create it in **production mode** in `asia-southeast1` (Singapore). Do not change or recreate an existing database.

## 2. Prepare a narrowly scoped deployment identity

The GitHub workflow deploys **Firestore rules only**. It does not deploy Hosting, Auth users, Firestore documents, POS data, or the app.

1. In Google Cloud Console for `la-princesa-pos-staging`, create or select a dedicated service account for staging rules deployment.
2. Grant that service account the **Firebase Rules Admin** role (`roles/firebaserules.admin`) on the staging project only.
3. Create a JSON key only if your organization permits service-account keys. Treat it like a password; never paste it into chat, commit it, or add it to `firebase-config.js`.
4. In GitHub, open this repository → **Settings → Secrets and variables → Actions → New repository secret**.
5. Name the secret exactly `FIREBASE_STAGING_SERVICE_ACCOUNT_JSON` and paste the complete service-account JSON as its value.

Prefer Workload Identity Federation over long-lived service-account keys when it is already configured. The workflow currently expects the repository secret above; do not add a key to source control.

## 3. Deploy the staging rules

1. Open the repository's **Actions** tab.
2. Select **Deploy Staging Firestore Rules**.
3. Press **Run workflow** and choose branch `owner-monitoring-staging`.
4. Confirm the run targets `la-princesa-pos-staging` and finishes successfully.
5. In Firebase Console → **Build → Firestore Database → Rules**, verify that the published rules match the repository's `firestore.rules`.

The workflow has a hard-coded staging project ID, checks the staging web config, and runs only `firebase deploy --only firestore:rules`. It refuses to run from another branch.

## 4. Provision separate test accounts and roles

Use test accounts only. Do not reuse production UIDs or production passwords.

1. In Firebase Console → **Authentication → Sign-in method**, enable **Email/Password**.
2. Create a staging Owner test account and a separate staging Staff test account.
3. In Firestore, create a `roles` document for each account. The document ID must be that test account's exact Authentication UID.
4. Owner document fields: `role` = string `owner`, `name` = string `Sheena Marie Cuizon`, and `email` = the staging Owner email.
5. Staff document fields: `role` = string `staff`, `name` = a test cashier name, and `email` = the staging Staff email.

Never create a role document for an unverified UID. The first Owner must be provisioned manually by the project administrator.

## 5. Staging-only functional test

1. Use only clearly marked test records. Do not import live sales, customers, inventory, or expenses.
2. Sign in on two separate devices with the staging Staff and Owner accounts.
3. Create a test sale and inventory change as Staff; verify the Owner view receives them.
4. Run Owner Clear Data only after confirming the test data is disposable.
5. Verify only Sales (`orders_v2`), Inventory (`inventory_v2`), Records (`deleted_orders_v1`), and Activity Log (`activity_log_v1`) are cleared.
6. Verify Owner/Staff roles, login, machines, printer settings, cash-drawer settings, and all other settings remain.
7. Reconnect a Staff device that was offline during the clear. Verify its old queued data is rejected and does not return.
8. Test receipt printing and the cash-drawer trigger separately. The clear-data patch must not alter either behavior.

## Safety boundaries

- Do not deploy these rules to production.
- Do not merge PR #31 wholesale into `main`; it contains broader staging infrastructure and app changes.
- Do not publish or install the staging APK as the live POS release.
- Emulator CI passing is not proof of Firebase deployment or real multi-device/offline behavior.
- Firestore rule changes can take time to propagate. Wait for propagation before testing active clients.
- Keep all real shop data and production accounts untouched.
