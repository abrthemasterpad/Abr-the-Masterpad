# Cosmic Universe — Android TV prototype

This is a new, isolated native Android prototype for the G07 / Android 11 target. It is not integrated into Free My TV or the stock launcher yet.

## Design goals

- 16:9 TV layout, high contrast, planet-like app tiles with labels below.
- D-pad navigation and visible focus state must be implemented by the host Activity.
- Locally rendered space scene: stars, nebula-like gradients, orbit paths, planets and occasional meteors. No video background and no network calls required for animation.
- Android 11 compatible; avoid Android 13-only shader APIs.
- A reduced-motion setting and a low-power mode should reduce frame rate and particle count.
- Never change Home preference as part of a prototype launch.

## Current state

- Compatibility review: `../FREEMYTV_G07_AUDIT.md`
- Initial rendering primitive: `CosmicSkyView.java`
- This is a rendering component, not a complete installable APK. A manifest, Activity, app icons, build configuration and on-device test are still required.

## Next steps

1. Create a minimal Android app wrapper with a launcher activity and D-pad focus handling.
2. Add a local app inventory / launch intents without accessibility scraping.
3. Run static compilation and APK build against Android SDK 30+.
4. Install as an ordinary app and test the scene, frame pacing, remote navigation and app launches.
5. Only after those pass, evaluate Home-mode integration separately with a verified recovery route.
