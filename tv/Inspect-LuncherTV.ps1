[CmdletBinding()]
param(
 [string]$TvHost = "10.117.141.27",
 [int]$Port = 5555,
 [string]$AdbPath = "$HOME\Downloads\platform-tools-latest-windows\platform-tools\adb.exe",
 [switch]$SetAsHome,
 [switch]$Launch
)
$ErrorActionPreference = "Stop"
$Package = "com.luncher.launcher"
$Activity = "$Package/.home.HomeActivity"
if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB not found at '$AdbPath'." }
& $AdbPath connect "$($TvHost):$Port" | Out-Host
$deviceLine = & $AdbPath devices | Where-Object { $_ -match "^$([regex]::Escape($TvHost)):$Port\s+device$" }
if (-not $deviceLine) { throw "TV is not in ADB 'device' state. Check debugging authorization and run adb devices." }
function Invoke-TvAdb {
 param([Parameter(Mandatory)][string[]]$Arguments)
 & $AdbPath -s "$($TvHost):$Port" @Arguments
 if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($Arguments -join ' ')" }
}
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$reportPath = Join-Path $outDir "tv-inspection-$stamp.txt"
$report = [System.Collections.Generic.List[string]]::new()
$report.Add("Cosmic Universe / Luncher TV inspection")
$report.Add("Captured: $(Get-Date -Format o)")
$report.Add("Target: $TvHost port $Port")
$report.Add("")
$report.Add("=== DEVICE ===")
$properties = @(
 @{Label="Manufacturer"; Key="ro.product.manufacturer"},
 @{Label="Model"; Key="ro.product.model"},
 @{Label="Device"; Key="ro.product.device"},
 @{Label="Android"; Key="ro.build.version.release"},
 @{Label="SDK"; Key="ro.build.version.sdk"},
 @{Label="Build"; Key="ro.build.display.id"},
 @{Label="Incremental"; Key="ro.build.version.incremental"},
 @{Label="Fingerprint"; Key="ro.build.fingerprint"},
 @{Label="Security patch"; Key="ro.build.version.security_patch"}
)
foreach ($property in $properties) {
 $value = (Invoke-TvAdb @("shell","getprop",$property.Key) | Out-String).Trim()
 $report.Add("$($property.Label): $value")
}
$report.Add("")
$report.Add("=== LUNCHER PACKAGE ===")
$pkg = Invoke-TvAdb @("shell","pm","path",$Package)
$report.Add(($pkg | Out-String).Trim())
$report.Add("")
$report.Add("=== CURRENT HOME RESOLVER ===")
$currentHomeResolver = Invoke-TvAdb @("shell","cmd","package","resolve-activity","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME")
$report.Add(($currentHomeResolver | Out-String).Trim())
if (-not $pkg) { throw "Luncher package '$Package' was not found on the TV. Install the official APK first." }
if ($SetAsHome) {
 Write-Host "Setting Luncher as preferred Home activity. Stock launcher will NOT be disabled." -ForegroundColor Yellow
 Invoke-TvAdb @("shell","cmd","package","set-home-activity",$Activity) | Out-Host
 Start-Sleep -Seconds 2
 $verifyHomeResolver = Invoke-TvAdb @("shell","cmd","package","resolve-activity","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME")
 $report.Add("")
 $report.Add("=== HOME RESOLVER AFTER SET-HOME ===")
 $report.Add(($verifyHomeResolver | Out-String).Trim())
 if (($verifyHomeResolver | Out-String) -notmatch [regex]::Escape($Package)) {
  $report.Add("WARNING: TV did not resolve HOME to Luncher after set-home-activity.")
  Write-Warning "Home resolver did not confirm Luncher. Stock launcher was not disabled."
 }
}
if ($Launch -or $SetAsHome) {
 Write-Host "Launching Luncher activity..." -ForegroundColor Cyan
 Invoke-TvAdb @("shell","am","start","-W","-n",$Activity) | Out-Host
}
$report | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host ""
Write-Host "Inspection report saved: $reportPath" -ForegroundColor Green
Write-Host "No packages were disabled or uninstalled." -ForegroundColor Green
