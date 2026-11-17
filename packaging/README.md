# Packaging

Native packages are built with `jpackage` (JDK 21), which also runs `jlink` to bundle a trimmed Java
runtime. Use the scripts in [`script/`](../script):

| Platform | Command | Output |
|---|---|---|
| Windows | `.\script\package.ps1` | Portable `.zip` (app image) |
| Windows | `.\script\package.ps1 -Type msi` | `.msi` installer (requires WiX Toolset 3.x) |
| Windows | `.\script\package.ps1 -Type all` | Both, as the release workflow does |
| macOS | `./script/package.sh` | `.dmg` |
| Linux | `./script/package.sh` | `.deb` (`./script/package.sh rpm` for `.rpm`) |

Packages can only be built on their own operating system. The
[release workflow](../.github/workflows/release.yml) builds all of them when a `v*` tag is pushed.

## Windows installer (.msi)

- jpackage in JDK 21 builds `.msi` files with **WiX Toolset 3.x** (`candle.exe` and `light.exe` on
  the `PATH`). WiX 4 and later are not supported by this JDK. Install it with
  `choco install wixtoolset`, or download the 3.14 binaries from the WiX GitHub releases and add the
  folder to the `PATH`.
- The installer is **per user**: it needs no administrator rights and installs under
  `%LOCALAPPDATA%`. It adds a Start menu entry and offers a desktop shortcut.
- `--win-upgrade-uuid` in `script/package.ps1` must **never change**: Windows uses it to recognize a
  new version as an upgrade instead of a second installation.
- A single self-contained `.exe` is not possible with jpackage: the app needs its bundled runtime
  folder next to the launcher. The `.msi` and the portable `.zip` are the two options.

## Icons

`packaging/icons/logo.png` is the source logo (square, transparent corners). Every other icon is
generated from it; never edit them by hand:

```bash
java script/GenerateIcons.java
```

| File | Used for |
|---|---|
| `modules/app/src/main/resources/icons/app-<size>.png` | Window title bar, taskbar and dialogs (JavaFX, 16–256 px) |
| `packaging/icons/app.ico` | Windows `.exe` and `.msi` (16–256 px) |
| `packaging/icons/app.icns` | macOS app and `.dmg` (16–1024 px) |
| `packaging/icons/app.png` | Linux packages and the README (512 px) |

Run the generator again after changing the logo and commit the regenerated files.

## Notes

- `modules/app/module-info.java` declares `requires java.naming` because Logback needs it at runtime
  even though Logback marks it optional; without it the packaged app does not start.
- Packages are not signed yet. Signing requires certificates that cannot be stored in the repository;
  they would be provided to the release workflow as encrypted secrets.
