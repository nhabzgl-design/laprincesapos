# Netlify deployment settings — La Princesa Owner POS

Connect the GitHub repository `nhabzgl-design/laprincesapos` to a separate Netlify site for the Owner PWA.

Use these settings for a draft/branch deploy:

- Branch to deploy: `ios-owner-monitoring-dev`
- Base directory: leave blank (repository root)
- Build command: leave blank
- Publish directory: `ios-owner`

The published folder contains `index.html`, `manifest.webmanifest`, `sw.js`, `icon.svg`, and `firebase-config.js`.

Do not connect this site to the existing production Staff POS domain. Do not deploy the development branch as the production site until Firebase project identity, deployed Firestore rules, Owner login, data reconciliation, and Android regression tests are complete.

After changing `firebase-config.js`, publish a new Netlify deploy and clear/reload the iPhone PWA if it still shows an older cached version.
