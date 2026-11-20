# Netlify deployment settings — La Princesa Owner Monitoring

Use a separate Netlify site for the iOS Owner Monitoring PWA. Never attach this site to the existing Staff POS production domain.

## Preview deployment (before release)

Connect GitHub repository `nhabzgl-design/laprincesapos` and select branch:

- Branch: `ios-owner-clear-data-sync`
- Base directory: leave blank (repository root)
- Build command: leave blank
- Publish directory: `ios-owner`

The published folder contains `index.html`, `manifest.webmanifest`, `sw.js`, `icon.svg`, and `firebase-config.js`.

Use this as a private/internal preview while Owner login, live data reconciliation, Firestore rules, and physical iPhone tests are pending. Do not share it as the production Owner URL yet.

## Production deployment (after release approval)

Only after the Android Staff integration and iOS Owner PWA are reviewed, merged to `main`, production Firestore rules are safely deployed to the verified `laprincesa-pos` project, Owner login and live data are verified, and iPhone testing passes:

- Production branch: `main`
- Base directory: blank
- Build command: blank
- Publish directory: `ios-owner`

Keep this as a separate Netlify site and separate domain from the Staff POS.

After changing `firebase-config.js` or publishing a new build, clear/reload the iPhone PWA if it still shows an older cached version.

## Explicit safety gates

- Do not deploy the development branch as the production site before the release gates pass.
- Do not use Owner Clear Operational Data until the matching Android generation-barrier code and Firestore rules are deployed and every Staff device has the compatible APK.
- Never add Firebase Admin credentials, service-account JSON, private keys, or user passwords to the PWA.
