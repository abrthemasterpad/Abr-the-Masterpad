# Cosmic Remote — native mobile companion

This module is an early native Android companion for the Cosmic Universe TV prototype. It does not mirror/share the phone screen and has no account, cloud relay, paid API or subscription flow.

## Implemented in this branch (code exists; build/device status pending)
- QR scanning for the TV pairing payload and manual host/port/key entry.
- Local WebSocket client.
- AES-GCM encryption of command payloads using a random 256-bit session key carried by the TV QR.
- D-pad/OK/Back controls.
- Trackpad gestures mapped to focus navigation inside the TV launcher.
- Incremental drawing strokes sent to the TV drawing canvas.
- Keyboard text that filters the TV launcher app list.
- Android speech-recognition intent that sends recognized text to the launcher.

## Known limitations — do not call this production-ready
- The pairing transport is currently WebSocket over the local network with AES-GCM message encryption, but no TLS server-identity pinning. Use only on a trusted local network until TLS/pinning and an active-attacker threat review are complete.
- Trackpad motion currently maps to focus steps rather than an arbitrary system-wide pointer.
- Keyboard/voice input filters this launcher's own app catalogue; it does not inject text into arbitrary third-party TV apps.
- The drawing board is a launcher-owned surface, not an Android system widget.
- Camera-to-TV streaming, freeform widget editing, RSS news, prayer-time calculations, sticky-note image boards, backup/restore and on-device AI are planned, not implemented here.
- No build or physical TV test is asserted by this README. Check GitHub Actions artifacts and the G07 device test log.

## Pairing
1. Start Cosmic Universe TV and keep it in the foreground.
2. Put the phone on the same local network.
3. Scan the TV QR from Cosmic Remote, then tap Connect.
4. Select Remote, Pad, Draw, Keys or Voice.
5. Disconnect when finished. Restarting the TV app generates a new pairing key.

## Privacy
The app contains no login and sends no telemetry. The local socket is used only for explicit controller commands. Voice uses the Android speech-recognition service available on the device; service/provider behavior may vary. Camera permission is not needed by the current prototype and should not be added until camera-to-TV is implemented.
