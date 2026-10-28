# Free My TV / Cosmic Universe — G07 Compatibility Audit

Status: source review only; **not installed or activated on the TV by this document**.

## Device facts captured from the user's TV over ADB

- Manufacturer: TCL
- Product model reported by Android: Smart TV
- Device: G07
- Android: 11 (SDK 30)
- Build display ID: RP1A.200622.001 release-keys
- Incremental: AR07
- Fingerprint: TCL/G07_4K_GB_NF/G07:11/RP1A.200622.001/AR17:user/release-keys
- Security patch: 2024-12-05
- TV ADB endpoint previously reachable: 10.117.141.27:5555
- Stock Home activity observed: com.google.android.apps.tv.launcherx/.home.HomeActivity
- Luncher is installed, but `cmd package resolve-activity ... CATEGORY_HOME` still resolves to Google's launcher after `cmd package set-home-activity`.

## Upstream source reviewed

Repository: https://github.com/simbasjungles/free-my-tv  
Files reviewed: `README.md` and `app/src/io/freemytv/HomeMode.java` on upstream main.

Upstream README describes Free My TV as a native Android app built without Gradle or third-party libraries, drawing its interface on a Canvas. It says Android 8–12 uses plain fills for shaders and lighter shadows, and Android 8–11 avoids GPU layers. This is relevant to SDK 30 but does not prove performance on this exact G07.

The source's `HomeMode.java` includes a TCL `DEBLOAT` list and a `PROTECTED` package list. Its mode-switch flow uses the TV's own ADB to set the Home activity and disable packages. On Google TV it can disable the stock launcher and Google recovery/setup package as part of switching modes. The README says its Original mode restores the original state and a watchdog switches back after repeated crashes.

## Compatibility conclusion

**Do not activate upstream Home mode on this G07 yet.** The device is Android 11 and its package fingerprint is TCL/G07_4K_GB_NF, but the upstream TCL list is not enough to certify every package on this firmware. The README's safety claims are upstream project claims, not a device-specific validation.

Before any Home-mode switch, gather the exact installed package list and compare every package that the switch could disable against this TV. In particular, do not manually disable `com.google.android.apps.tv.launcherx`, `com.google.android.tungsten.setupwraith`, `com.android.tv.settings`, system UI, input, Bluetooth/remote, update, TV tuner, or factory/settings packages. Keep the stock launcher enabled until the replacement has been opened and tested.

## Planned implementation, isolated from the existing site

This branch is an audit and planning area. The existing root portfolio/site files are untouched. Proposed next implementation steps:

1. Add a Cosmic Universe design specification for the Android TV interface: D-pad focus, readable labels, reduced motion/performance mode, no video background, and local-only rendering for stars/planets/meteors.
2. Build a device-specific package audit/checklist before integrating upstream HomeMode behavior.
3. Create a separate experimental app module or clearly isolated fork; do not copy upstream source into production until licensing, provenance, and build/reproducibility checks are recorded.
4. Test app launch and D-pad navigation before changing the system Home handler.
5. Preserve an explicit Original/recovery path and verify it from ADB before any Home-mode activation.

## Non-goals / safety constraints

- No rooting or firmware flashing proposed.
- No paid APIs required for the cosmic animation.
- No changes to the default `main` branch.
- No stock/system package disabling until device-specific review and explicit confirmation.
