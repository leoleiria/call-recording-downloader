# Builds the Windows packages, each with a bundled Java runtime (jlink + jpackage).
#
#   .\script\package.ps1               # portable .zip (no installation needed)
#   .\script\package.ps1 -Type msi     # .msi installer; requires WiX Toolset 3.x on the PATH
#   .\script\package.ps1 -Type all     # both (used by the release workflow)
#
# Output goes to dist\. Requires JDK 21+ (jpackage ships with the JDK).
param(
  [ValidateSet('zip', 'msi', 'all')]
  [string]$Type = 'zip'
)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$name = 'Call Recording Downloader'
# Fixed forever: Windows uses it to recognize new versions as upgrades of the installed app.
$upgradeUuid = 'b7b04f65-678a-4640-bb95-cb6a14a019f5'

if ($Type -ne 'zip' -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
  throw 'The .msi needs WiX Toolset 3.x (candle.exe, light.exe) on the PATH. See packaging/README.md.'
}

.\mvnw.cmd -B -ntp clean verify
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }

# jpackage needs a purely numeric version: 0.1.0-SNAPSHOT -> 0.1.0
$version = (.\mvnw.cmd -B -q -ntp help:evaluate '-Dexpression=project.version' -DforceStdout).Trim()
$appVersion = $version.Split('-')[0]

if (Test-Path dist) { Remove-Item -Recurse -Force dist }
New-Item -ItemType Directory dist | Out-Null

$commonArgs = @(
  '--name', $name,
  '--app-version', $appVersion,
  '--vendor', 'leoleiria',
  '--description', 'Download call recordings, voicemails and transcriptions from Ringover. Not affiliated with Ringover.',
  '--copyright', 'Copyright (c) 2026 @leoleiria (https://github.com/leoleiria)',
  '--icon', 'packaging\icons\app.ico',
  '--module-path', 'modules\app\target\modules',
  '--jlink-options', '--strip-debug --no-man-pages --no-header-files',
  '--module', 'io.github.leoleiria.callrecording.app/io.github.leoleiria.callrecording.app.Launcher'
)

if ($Type -in 'zip', 'all') {
  jpackage --type app-image --dest dist\image @commonArgs
  if ($LASTEXITCODE -ne 0) { throw 'jpackage (app-image) failed' }
  $zip = "dist\call-recording-downloader-$appVersion-windows-x64.zip"
  Compress-Archive -Path "dist\image\$name" -DestinationPath $zip
  Remove-Item -Recurse -Force dist\image
  Write-Host "Portable version: $zip"
}

if ($Type -in 'msi', 'all') {
  # Per-user install: no administrator rights needed, installs under %LOCALAPPDATA%.
  jpackage --type msi --dest dist\msi @commonArgs `
    --license-file LICENSE `
    --win-per-user-install `
    --win-upgrade-uuid $upgradeUuid `
    --win-menu --win-menu-group $name `
    --win-shortcut --win-shortcut-prompt
  if ($LASTEXITCODE -ne 0) { throw 'jpackage (msi) failed' }
  $msi = "dist\call-recording-downloader-$appVersion-windows-x64.msi"
  Move-Item (Get-ChildItem dist\msi\*.msi | Select-Object -First 1).FullName $msi
  Remove-Item -Recurse -Force dist\msi
  Write-Host "Installer: $msi"
}
