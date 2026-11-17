# Builds the project and starts the desktop app from source.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
.\mvnw.cmd -B -ntp -q install -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
.\mvnw.cmd -B -ntp -pl modules/app javafx:run
