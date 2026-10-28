# Masterpad Launcher — Product & Engineering Master Specification

**Status:** Active implementation baseline; not a claim of finished product.  
**Target:** Native Android TV launcher plus native Android mobile companion.  
**Constraints:** No user accounts, no paid APIs, no subscription paywall, local-first storage, no screen sharing.  
**Reference implementation:** Existing Cosmic Universe TV prototype on the feature branch.  
**Evidence rule:** Mark each capability as IMPLEMENTED, TESTED, BLOCKED, or PLANNED. Never label a design document as a working feature.

## 1. Product ambition

Build a premium, remote-first, customizable Android TV home experience with a mobile companion that acts as a control surface. The TV and phone are paired locally. The phone can act as a touchpad, directional remote, keypad, drawing surface, voice-input source, and optional camera source for features rendered inside the launcher. It does not mirror or share the phone screen.

“World's first” is not a verified claim and must not be used as a factual marketing statement without a defensible novelty review.

## 2. Hard product constraints

- No account/login flow.
- No subscription/paywall.
- No paid API dependency.
- Core Home, app discovery, layouts, notes, preferences, static wallpaper and local pairing must work without internet.
- Regional news may use configurable public RSS/Atom feeds directly, with source attribution and clear unavailable/offline states. This is feed consumption, not a promise of a free unlimited news API. Respect publisher terms and copyright; show summaries/headlines and open the source for full reporting unless republication rights permit more.
- Prayer times should be calculated locally from user-selected location/method/timezone where practical; location access must be optional, with manual city selection supported.
- AI features must not imply cloud-grade generative intelligence without a model. Initial offline analysis can use local rules/structured summarization. On-device model support is a later optional module only after model size, license, RAM and latency are measured.
- Do not use accessibility services to secretly inject global input. System-wide control beyond the launcher requires explicit Android-supported capability/permission and must be disclosed.
- Never claim a mobile control can operate arbitrary third-party TV apps until the relevant system capability is implemented and tested.

## 3. Experience architecture

### TV surfaces
1. Home canvas: background, widgets, app shortcuts, movable/resizable widget frames.
2. App library: discovered launchables, favorites, search, categories.
3. Widget editor: add/move/resize/configure, snap/grid toggle, undo/redo, safe-area boundaries.
4. Notes board: sticky notes, image pins, clipped references, todo checklist and due dates.
5. News reader: feed/source list, headline cards, article-style reading view with source link and attribution.
6. Settings: appearance, layout, input, accessibility, privacy, backup/restore, pairing, diagnostics.
7. Mobile pairing: short-lived QR containing local endpoint and secret, rotate/disconnect controls.
8. Media/wallpaper: static first; optional video only after lifecycle/memory tests.

### Mobile companion
1. Pair by scanning TV QR or entering local address manually.
2. Touchpad with relative pointer movement and tap/click, only within supported launcher control scope.
3. D-pad/OK/Back remote.
4. Keyboard text entry for launcher search and supported text fields.
5. Drawing pad sends incremental stroke points to a TV canvas widget.
6. Voice input uses the device's available speech-recognition intent/service; availability and offline operation depend on installed speech services and language packs.
7. Camera feature streams selected frames only to a TV-side launcher surface after explicit user action; no screen sharing, no background camera, no recording by default.
8. Connection state, latency indicator, disconnect/re-pair, haptic toggle and sensitivity controls.

## 4. Freely configurable canvas model

Every widget is a versioned entity:
- id, type, schemaVersion
- x, y, width, height in logical canvas units
- zIndex and stacking policy
- locked, visible, pinned flags
- per-widget configuration JSON validated against type schema
- creation/update timestamps
- asset references, not raw binary data in layout rows

Store layout in local database. Use transactions for moves/resizes. Support snap-to-grid, alignment guides, multi-select, duplicate, lock, z-order, undo/redo and reset. Keep a 5% TV safe margin as an initial visual guideline, then validate on hardware. On small screens, the mobile editor may pan/zoom the canvas rather than force the TV layout into a phone-sized grid.

The drawing pad widget uses a bounded vector-stroke model (stroke id, color, width, point sequence, completion state). Stream incremental points rather than whole bitmaps per move. Coalesce high-frequency pointer samples, batch messages per animation frame, cap payloads and render with a dedicated canvas layer. A frame budget must be measured; “zero lag” is not a truthful guarantee across Wi-Fi and device hardware.

## 5. Local pairing and protocol

- TV displays a QR pairing payload containing LAN address, port, protocol version and a cryptographically random short-lived secret.
- Mobile scans QR, validates schema/version, and opens a local socket connection.
- Pairing is local-network only; no cloud relay or account.
- Pairing token rotates on explicit disconnect/re-pair and expires.
- Validate every message type, size, sequence and numeric range.
- Use authenticated encryption or a TLS channel with pinning before calling the transport corporate-grade. Do not ship a plaintext-secret WebSocket as production-ready.
- Do not claim network isolation: devices on the same Wi-Fi may be hostile. Show an explicit pairing state and provide immediate disconnect.
- Use bounded reconnect with cancellation; never reconnect forever in background.
- NSD/mDNS discovery can be a convenience fallback, not the only connection method.
- TV may accept only one controlling peer by default; make multi-controller behavior explicit.

### Protocol messages (versioned)
- hello / hello_ack
- pointer_delta, pointer_tap, pointer_long_press
- key, text_input
- stroke_begin, stroke_points, stroke_end, stroke_cancel
- voice_text
- camera_start, camera_frame, camera_stop
- ping, pong, disconnect, error

Every message includes protocol version, request/sequence ID, bounded payload and typed result. Camera frames use a separate bounded binary channel or compressed-frame pipeline; do not send large base64 frames in JSON. Add rate limits and backpressure.

## 6. Data and privacy architecture

- Local-first repository pattern, Room for relational state and DataStore for small preferences.
- No login/account tables.
- No analytics upload by default.
- No installed-app inventory upload.
- User images and notes remain local unless the user explicitly exports them.
- Export/import uses versioned manifest, staged validation, path-traversal and archive-bomb protection, atomic commit/recovery.
- RSS cache stores source URL, headline, publisher, timestamp, excerpt and source link; do not silently republish complete copyrighted articles.
- Prayer settings store manual location/method/timezone; exact GPS permission optional.
- Camera and microphone activation is visible, user-initiated and stops immediately when disabled or disconnected.
- Diagnostics redact private paths, content and pairing secrets.

## 7. Feature feasibility and dependencies

| Feature | Initial implementation | External service/API required? | Gate |
|---|---|---|---|
| App discovery and launch | Android PackageManager/launch intents | No | Query visibility + OEM test |
| Custom widgets/canvas | Native UI + Room layout model | No | Layout persistence + focus tests |
| Clock/date | Local system time | No | Timezone/locale tests |
| Reminders/todos | Local DB + AlarmManager/WorkManager as appropriate | No | reboot/time-change tests |
| Prayer times | Local calculation library or audited algorithm | No network API | Validate method, coordinates, timezone and DST |
| Regional news | Public RSS/Atom sources, cached | Internet feed URLs, not API key | Publisher terms, parsing, offline/error tests |
| AI analysis | Deterministic local insight first | No API | Clearly label limitations; later on-device model benchmark |
| Mobile remote | LAN protocol | No cloud | Authenticated encryption + latency tests |
| Drawing pad | Incremental stroke protocol | No cloud | Backpressure + visual parity tests |
| Voice input | Android speech recognizer | Device speech service may be required | Permission, language/offline capability checks |
| Webcam | User-triggered local camera frames | No cloud | Permission, privacy, codec/latency test |
| Video wallpaper | Media3, later phase | No API | Surface/player lifecycle and memory gates |
| Themes/import | Local assets + versioned schema | No API | Malicious archive test |
| Premium/paywall | Not in product | None | Must remain absent |

## 8. Architecture

Recommended principles:
- Kotlin for new code; Compose-first UI for mobile and modern TV surfaces where compatibility permits.
- Share domain models/protocol contracts; keep TV D-pad and mobile touch UI as separate presentations.
- UI -> ViewModel/state holder -> repository -> local data/platform adapter.
- Coroutines/Flow for state and asynchronous operations; main-safe repositories.
- Stable IDs for app targets and widgets.
- Room transactions for related state, DataStore for small preferences.
- Media3 only for the optional media feature after static wallpaper is stable.
- Narrow platform adapters for package discovery, Home-role state, alarms, camera, speech and local network.
- Keep module count modest; separate TV/mobile entry modules when dependency and manifest boundaries justify them.

## 9. Release and testing gates

### Automated
- unit tests for layout normalization, grid placement, prayer-time inputs, feed parser, protocol validation and state machines
- persistence/migration and backup rollback tests
- UI tests for focus, D-pad, Back, drag/resize and accessibility semantics
- adversarial tests for oversized messages, malformed QR, invalid paths, duplicate IDs and stale async results
- Macrobenchmark journeys for cold/warm/hot startup and widget interaction
- release APK manifest/dependency/native library audit
- ABI and 16 KB page-size verification where native libraries are present

### Physical device
- Android TV emulator plus TCL G07 Android 11 target
- D-pad/Back/Home behavior and recovery
- Wi-Fi pairing and disconnect/reconnect
- drawing update and latency measurement
- background/foreground and process-death restoration
- low-memory and unsupported-media fallback
- install/uninstall and default-Home recovery

Statuses must be PASS, FAIL, BLOCKED or NOT RUN. Never mark a physical test passed because an emulator passed.

## 10. Delivery phases

**Phase 0 — Source/build baseline:** inspect Gradle, manifests, SDK and CI; fix build reproducibility.
**Phase 1 — TV core:** app discovery, stable favorites, polished home canvas, focus, local persistence, layout editor.
**Phase 2 — Mobile local control:** QR pairing, authenticated encrypted transport, D-pad/trackpad/keypad and text input.
**Phase 3 — Live drawing:** incremental strokes, TV drawing widget, rate limits and latency instrumentation.
**Phase 4 — Productivity widgets:** clock/date, todo magnets, pinned images, reminders, backup/restore.
**Phase 5 — Prayer/news:** audited local prayer calculations and RSS/Atom source management.
**Phase 6 — Voice/camera:** opt-in mobile voice text and camera-to-TV feature.
**Phase 7 — Advanced media/AI:** video wallpaper and optional on-device model only after performance/license tests.
**Phase 8 — Release hardening:** security, compatibility, physical-device evidence, signed artifact and recovery guide.

## 11. Current known baseline

Existing branch feat/cosmic-universe-freemytv-audit contains a Java-based standalone TV prototype with a custom background and app tiles. The draft PR explicitly says the APK build had not yet been confirmed and the TV had not been tested. This spec does not change those facts.

## 12. Definition of done

A feature is done only when code exists, a reproducible test is present, the test was run, the result is recorded, failure behavior is defined, and privacy/permission implications are reviewed. “Designed,” “coded,” “built,” “installed,” and “validated on hardware” are separate states.
