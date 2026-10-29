# Advanced launcher build status

**Last reviewed:** 2026-10-29  
**Branch:** feat/advanced-launcher-product-foundation  
**PR:** https://github.com/abrthemasterpad/Abr-the-Masterpad/pull/3

## Code present in branch
- Product and engineering master specification.
- TV prototype: QR pairing panel, ephemeral random 256-bit pairing secret, WebSocket server, AES-GCM command payloads, per-connection HMAC-derived session key, sequence checks, app-list filtering and TV-side drawing surface.
- Mobile prototype: QR scanning, local WebSocket client, AES-GCM command payloads, D-pad/OK/Back, trackpad mapped to focus movement, incremental drawing, text filtering and speech-recognition intent.
- Standard launcher and Leanback discovery merged and deduplicated by component identity.\n- Initial workspace layer: live local clock/date, add/edit/delete sticky notes, add/edit/delete checklist tasks, completion toggle, drag positioning, D-pad movement, and SharedPreferences JSON persistence for card text/type/position/completion. This is an initial widget slice; no image magnets, full canvas editor, reminders or backup/restore yet.
- CI workflow requests debug APKs for both TV and mobile modules.

## Not yet proven
- Build success is pending the GitHub Actions result.
- No APK has been installed or tested on the TCL G07 in this pass.
- No latency number is measured for drawing.
- No TLS server-identity pinning yet. Payloads are AES-GCM encrypted with a fresh per-connection key derived from a challenge, but production security still needs a full active-attacker review and TLS/pinning.
- Trackpad navigation is focus-step based, not a system-wide pointer.
- Keyboard and voice text currently filter this launcher’s app catalogue only.
- Drawing is a launcher-owned canvas overlay, not a general Android widget.
- Webcam-to-TV, image magnets, reminders/schedules, prayer-time calculation, regional RSS news reader, AI analysis, advanced wallpaper/video, export/import and release hardening remain planned. The widget layer is not yet hardware-tested; its drag, persistence and TV focus behavior require CI and device verification.

## Required next gates
1. Finish CI and fix every build failure.
2. Add unit tests for crypto envelope validation, sequence/replay behavior, protocol bounds and drawing coordinate limits.
3. Install both APKs on an emulator and run a paired-session smoke test.
4. Install TV APK on TCL G07 and verify QR reachability, remote navigation, drawing, app launch and clean disconnect.
5. Add TLS identity pinning before use on untrusted networks.
6. Measure end-to-end drawing latency and dropped/coalesced points under repeated Wi-Fi trials.
7. Expand the initial widget layer into a complete editor with resize, alignment/snap, z-order, undo/redo and schema migrations.
