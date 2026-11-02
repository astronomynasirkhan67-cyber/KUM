# KUM — Kids Usage Manager (MVP)

An offline-first Android prototype. It does not need an account, cloud server, analytics SDK, or runtime internet connection.

## Current MVP
- Parent dashboard protected by Android strong biometric authentication.
- One Protection ON/OFF switch.
- When ON, a local Android `VpnService` installs IPv4 and IPv6 default routes and discards packets. This blocks Wi-Fi and mobile-data internet traffic for the device while the VPN remains active.
- When OFF, the VPN service stops and normal connectivity returns.

## Important limitations
- Android displays a system VPN-consent dialog the first time. The user must approve it.
- A normal app cannot silently force VPN permission, prevent the user from revoking it, or guarantee protection after force-stop/uninstall/reboot on every device.
- Strong biometric authentication may use a supported strong biometric, not necessarily fingerprint-only on every phone. Enrol the parent's fingerprint in Android Settings.
- This MVP blocks internet for the whole device; it does not yet implement per-app allow/deny lists.
- VPN routing and OEM battery/background restrictions vary by Android version and manufacturer. Test on the target phone before relying on it for child safety.
- This app does not block emergency calling or replace Android's supervised/managed-device controls.
- The Android build downloads Gradle/Android dependencies during CI. The installed app itself has no online features and makes no network requests.

## Build
Push this repository to GitHub, then run **Actions → Build KUM APK → Run workflow**. Download the `KUM-debug-apk` artifact.
