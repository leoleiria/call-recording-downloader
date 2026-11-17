# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Desktop app (Windows, macOS, Linux) to search the calls with a contact in any period and show the
  number of calls, recordings (voicemails included) and transcriptions.
- Download of recordings and transcriptions, recordings only, or transcriptions only, into
  `<folder>/<number>/`, with readable `.txt` transcriptions, the original JSON in
  `OriginalTranscription_Json/` together with a `manifest.csv` index.
- Idempotent, atomic and cancellable downloads.
- Ringover API client with rate limiting (2 requests/s), retries and cursor pagination; periods
  are split into 14-day requests, up to one year (27 requests).
- API key never stored, masked in the UI with an optional reveal, and masked in all logs.
- User interface in the 24 official languages of the European Union.
- Application logo in the window title bar, taskbar, dialogs, screens and all installers.
- Packaging scripts and release workflow producing self-contained packages: Windows `.msi`
  installer (per user, no admin rights) and portable `.zip`, macOS `.dmg`, Linux `.deb`.

[Unreleased]: https://github.com/leoleiria/call-recording-downloader/commits/main
