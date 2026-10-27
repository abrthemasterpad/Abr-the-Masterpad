# Cosmic Universe TV — Luncher setup

This workspace is for developing a custom Android TV launcher experience using Luncher as the current installed foundation. It does not modify the upstream Luncher repository.

## Known TV details

- iFFALCON 58-inch Android TV
- Android TV 11
- Previously reported firmware: V8-R51AT01-LF1V126.020732
- Previously reported incremental build: AR07
- ADB endpoint: 10.117.141.27:5555
- Luncher package: com.luncher.launcher
- Luncher Home activity: com.luncher.launcher/.home.HomeActivity
- Stock Home activity observed: com.google.android.apps.tv.launcherx/.home.HomeActivity

The script reads live TV properties to verify whether current firmware matches these known values.

## Inspect TV

From PowerShell in the repository folder:

    powershell -ExecutionPolicy Bypass -File .\tv\Inspect-LuncherTV.ps1

It connects to the known TV IP, verifies Luncher, records firmware/build properties and reports the current HOME resolver. Report is written to tv/reports/.

## Set Luncher as Home

After confirming Luncher opens and works with the TV remote, run:

    powershell -ExecutionPolicy Bypass -File .\tv\Inspect-LuncherTV.ps1 -SetAsHome -Launch

This asks Android package manager to set Luncher as preferred HOME activity and launches it. It does not disable, uninstall, or alter the stock launcher package. It records the HOME resolver after the attempt.

If the HOME resolver does not change, do not disable the stock launcher. Use the report to choose a firmware-appropriate method instead.

## Cosmic Universe target

- Small spherical/planet-like app icons with labels underneath
- Optional orbiting motion around focused icons
- Continuously rendered cosmic backdrop: stars, planets, nebulae and meteor trails
- D-pad and remote-first navigation
- Performance controls for Android TV 11 hardware
- No live-video background, paid API, or cloud dependency

## Safety

- Do not disable the stock launcher unless a recovery path has been tested.
- This script does not root the TV or flash firmware.
- Firmware compatibility and rootability cannot be concluded from the firmware string alone.
- Do not commit generated device reports if they contain identifiers you do not want public.
