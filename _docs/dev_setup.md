# Development setup

## Requirements

- **JDK 21** (any distribution; CI uses Eclipse Temurin). `jpackage` ships with the JDK.
- Nothing else: the Maven Wrapper (`mvnw`) downloads the right Maven version.

Make sure `JAVA_HOME` points to JDK 21 before running the wrapper:

```bash
java -version   # must print 21 or newer
```

## Build and test

```bash
./mvnw verify
```

On Windows use `mvnw.cmd` instead of `./mvnw`. `verify` compiles with `-Xlint:all -Werror`, runs all
tests and checks formatting.

## Run from source

```bash
./script/run_dev.sh          # macOS / Linux
.\script\run_dev.ps1         # Windows
```

Or directly: `./mvnw install -DskipTests` once, then `./mvnw -pl modules/app javafx:run`.

## Formatting

Code follows [google-java-format](https://github.com/google/google-java-format) (2-space indent),
enforced by Spotless. Fix formatting before committing:

```bash
./mvnw spotless:apply
```

## Packaging

```bash
./script/package.sh              # macOS → .dmg, Linux → .deb
./script/package.sh app-image    # portable folder
.\script\package.ps1             # Windows → portable .zip
.\script\package.ps1 -Type msi   # Windows .msi installer (needs WiX Toolset 3.x)
.\script\package.ps1 -Type all   # Windows .zip + .msi
```

See [packaging/README.md](../packaging/README.md#windows-installer-msi) for installing WiX.

Packages land in `dist/` and include a trimmed Java runtime built by `jlink`, so users do not need
Java installed. Packages must be built on the target operating system; the
[release workflow](../.github/workflows/release.yml) does this on GitHub-hosted runners.

## Project layout

```
.github/            CI, release workflow, issue and PR templates
_docs/              developer documentation (this folder)
docs/               user documentation
modules/
  core/             domain, use cases, file storage — no HTTP, no UI
  ringover-client/  Ringover API adapter (java.net.http + Jackson)
  app/              JavaFX user interface, translations, logging configuration
packaging/          packaging notes and (future) icons
script/             run and package helpers
```

See [architecture.md](architecture.md) for how the modules fit together.

## Manual test against the real API

Automated tests never call Ringover; they use a local fake server. Before a release, test once with
a real key (Calls → Read only) on a contact with recordings, voicemails and transcriptions, in both a
short (≤ 14 days) and a long (several months) period, and confirm that the log files contain no API key.

## Logs

The app writes to `~/.call-recording-downloader/logs/app.log`. Set the `crd.logDir` system property
to write elsewhere.
