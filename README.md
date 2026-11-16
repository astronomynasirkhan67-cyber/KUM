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


## App list and VPN filtering update

- The app picker now shows only user-installed apps with launcher icons. Android/system and preinstalled system apps are hidden from the picker.
- Switch ON means **Internet allowed**. Switch OFF means **Internet blocked** while Protection is active.
- Allowed package names are excluded from the local VPN; all other app traffic is routed into the local TUN interface and discarded.
- The dashboard distinguishes an active VPN tunnel from a VPN that is still starting or has failed.
- Android requires the user to approve the VPN connection. A VPN/key indicator may be shown by Android while this filtering VPN is active.

Build with `gradle assembleDebug`. Test on a spare device: with Protection ON and no apps allowed, verify that a browser cannot load a site; allow only one test app and verify that only that app regains internet. Disable any other VPN during the test.
