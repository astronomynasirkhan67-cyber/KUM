# KUM - Kids Usage Manager (VPN allow-list build)

Parent-locked (fingerprint) parental internet control.

- Uses Android `VpnService`; Accessibility Service is not required.
- Parent turns ON the switch beside apps that are allowed to access the internet. The installed-app list includes packages without launcher icons too.
- While protection is ON, every other app's IPv4 and IPv6 traffic is routed into a local VPN and discarded.
- Example: allow WhatsApp only; all other apps are blocked.
- No traffic is forwarded, inspected, decrypted, recorded, or sent to a server.
- Android requires a one-time system VPN consent dialog and an ongoing foreground-service notification while a VPN is active. Android may show a VPN/key indicator; third-party apps cannot legitimately hide the system VPN indicator.
- The app does not require Play Protect to be disabled. Sideloading may still require the user to approve installing apps from that source, and Play Protect may display its normal warning/scanning UI.

Build: run the GitHub Actions workflow or `./gradlew assembleDebug`.
