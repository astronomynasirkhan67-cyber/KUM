# Publishing KUM on Google Play (phone-only steps)

## A. Build the signed bundle (.aab) on GitHub
1. Push this project to GitHub (private repo is fine).
2. Actions > "1 - Create upload keystore (run once)" > Run workflow. Open the finished run, download artifact `KUM-upload-keystore` (kept 1 day), keep a safe copy of the 3 files forever.
3. Repo Settings > Secrets and variables > Actions > New secret. Add 4 secrets:
   - `KEYSTORE_BASE64` = the long text inside `keystore-base64.txt`
   - `KEYSTORE_PASSWORD` = the password from `keystore-info.txt`
   - `KEY_PASSWORD` = same password
   - `KEY_ALIAS` = `kum`
4. Actions > "2 - Build Play Store AAB" > Run workflow. Download `KUM-play-store-aab` (contains `app-release.aab`).
Never lose the keystore: you need the same one for every update.

## B. Play Console
1. Create a Play Console developer account (one-time fee; check the current fee and identity verification on Google's page).
2. Create app > App category: Tools or Parenting. Free.
3. Upload `app-release.aab` (Production, or Closed testing first). Accept Play App Signing.
4. Store listing: icon `play-store-assets/app-icon-512.png`, feature graphic `play-store-assets/feature-graphic-1024x500.png`, at least 2 phone screenshots (take them from your phone), short + full description.
5. Privacy policy: host `PRIVACY_POLICY.md` as a web page (e.g. GitHub Pages or your website), fill the [DATE] and [YOUR EMAIL], paste the link.
6. App content > Data safety: collects no data, shares no data.
7. Declare the VPN service accurately in Play Console. Explain that the parent explicitly enables a local VPN to enforce an app internet allow-list; blocked app packets are discarded locally and are not inspected, logged, or forwarded. Provide any required VPN disclosure and consent flow.
8. Content rating questionnaire, target audience: adults/parents (not children).
9. If your developer account is a new personal account, Google may require a closed test with a minimum number of testers for some days before production. Check the current rule in Play Console.
