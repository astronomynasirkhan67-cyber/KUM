# KUM - Kids Usage Manager

Parent-locked (fingerprint) app that stops kids from using the phone.

- No VPN, so no key icon in the status bar.
- Protection ON: every app except KUM, Home and Phone is covered by a loading spinner that never finishes (no message, no popup).
- Setup: install the APK, open KUM, tap "Enable KUM Guard (Accessibility)" and switch it on, come back and tap "Turn Protection ON".
- Android 13+ may block this for APKs installed outside the Play Store: open Settings > Apps > KUM > three dots > "Allow restricted settings", then enable KUM Guard.
- While protection is ON, Settings is also covered, so turn protection OFF in KUM first (fingerprint) when you need Settings.

Build: push to GitHub, the Actions workflow produces `KUM-debug-apk`.
