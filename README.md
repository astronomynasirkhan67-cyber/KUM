# KUM - Kids Usage Manager

Parent-locked (fingerprint) app that stops kids from using chosen apps.

- No VPN, so no key icon in the status bar.
- In the dashboard the parent switches ON the apps to block (video, games, browsers, social...). Switch OFF = allowed.
- Protection ON: only the blocked apps are covered by a loading spinner that never finishes (no message, no popup). Home, Phone, KUM and every allowed app work normally.
- The dashboard locks again every time KUM is left, so the fingerprint is needed each time.
- Setup: install the APK, open KUM, tap "Enable KUM Guard (Accessibility)" and switch it on, come back, choose apps, tap "Turn Protection ON".
- Android 13+ may block this for APKs installed outside the Play Store: Settings > Apps > KUM > three dots > "Remove setting restrictions" (or "Allow restricted settings"), then enable KUM Guard. Also switch off "Pause app activity if unused" for KUM.
- Only block Settings if you want to stop the child from switching KUM Guard off. Then turn protection OFF in KUM first when you need Settings.

Build: push to GitHub, the Actions workflow produces `KUM-debug-apk`.
