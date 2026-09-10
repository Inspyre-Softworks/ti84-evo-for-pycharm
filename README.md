# TI-84 Evo for PyCharm

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/33854?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/33854-ti-84-evo)
[![Documentation Status](https://readthedocs.org/projects/ti84-evo-for-pycharm/badge/?version=latest)](https://ti84-evo-for-pycharm.readthedocs.io/en/latest/?badge=latest)
[![Build](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/actions/workflows/ci.yml/badge.svg)](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/actions/workflows/ci.yml)
[![Sourcery](https://img.shields.io/badge/Sourcery-enabled-brightgreen)](https://sourcery.ai)

Build TI Python projects in PyCharm and transfer them directly to a TI-84 Evo over USB.

TI-84 Evo adds a dedicated tool window to PyCharm for sending Python programs, managing calculator variables, capturing screenshots, and keeping multi-file projects in sync. It also provides editor completion and documentation for the calculator's built-in `ti_*` modules—without installing desktop replacements or adding helper files to your calculator.

> [!IMPORTANT]
> This is an alpha hardware-integration project. Single-file Python upload and read-only directory browsing have been tested on a physical TI-84 Evo. Other write and synchronization workflows still need broader device testing. Keep backups of important calculator data.

## Highlights

- Upload the current Python file to RAM or Archive.
- Configure, push, and pull multi-file Python projects.
- Skip unchanged files during project pushes.
- Browse calculator variables with their type, size, and storage location.
- View and export variables in their native Evo format.
- Create or edit numbers, lists, and matrices.
- Move variables to Archive or delete them with confirmation.
- Convert PNG, JPEG, GIF, and BMP images for use with `ti_image`.
- Capture the calculator screen and save it as a full-resolution PNG.
- Read and copy detailed calculator information.
- Get completion, parameter hints, quick documentation, and inspections for `ti_draw`, `ti_image`, `ti_system`, `ti_plotlib`, `ti_hub`, and `ti_rover`.
- Use the companion PowerShell CLI and optional Windows Explorer context menus outside PyCharm.

## Find what you need

| Goal | Documentation |
| --- | --- |
| Install, connect, and upload a first program | [Getting started](docs/guide.rst) |
| Browse, edit, archive, or delete calculator variables | [Calculator files and images](docs/calculator-files.rst) |
| Configure, push, or pull a multi-file project | [Multi-file projects](docs/projects.rst) |
| Use PowerShell or Windows Explorer | [Companion CLI](docs/cli.rst) |
| Fix detection, timeout, conflict, or conversion problems | [Troubleshooting](docs/troubleshooting.rst) |
| Understand or contribute to the implementation | [Protocol overview](docs/protocol.rst) · [Development](docs/development.rst) |
| Enable developer-only Marketplace diagnostics | [Marauders Lock](docs/marauders-lock.rst) |

## Requirements

- PyCharm 2026.2 or newer
- A TI-84 Evo and a USB data cable for calculator operations

Java 25 is only required when building the plugin from source or using the companion CLI.

## Install

In PyCharm:

1. Open **Settings → Plugins → Marketplace**.
2. Search for **TI-84 Evo**.
3. Select **Install**, then restart PyCharm if prompted.

You can also download a plugin ZIP from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/33854-ti-84-evo) or a matching [GitHub release](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/releases), then install it from **Settings → Plugins → ⚙ → Install Plugin from Disk**.

## Quick start

1. Connect your TI-84 Evo to the computer with a USB data cable.
2. Open **View → Tool Windows → TI-84 Evo**.
3. Select **Refresh devices**.
4. Use **Read attributes** to confirm communication with the calculator.
5. Open a `.py` file and select **Upload current Python file**.
6. Choose a 1–8 character calculator program name and send it to RAM or Archive.

Uploading replaces an existing calculator program with the same name. The plugin reads the current editor contents, so unsaved changes are included.

## Work with calculator files

Open **Calculator Files** to browse variables on the connected calculator. From there you can:

- inspect a variable and export a lossless native copy;
- create or replace numbers, lists, and matrices;
- move selected RAM variables to Archive; or
- delete selected variables after reviewing a confirmation prompt.

Deleting one of the built-in lists `L1`–`L6` clears its contents while preserving its List Editor slot. Other selected variables are removed and cannot be recovered.

To upload a picture, choose **Upload picture**. Standard desktop image formats are converted to a compressed Evo Python image variable, while existing `.8ci2`, `.8ca2`, and `.8xv2` files are transferred without conversion. Image size and palette limits are available under **Transfer settings**.

## Multi-file projects

Choose **Configure project** to map local Python files to calculator program names and select RAM or Archive for each file. The plugin stores the configuration in a project-local `.ti84-evo-project` file that can be committed to source control:

```properties
@always-push-all=false
lib/drawing.py=DRAW|Archive
lib/state.py=STATE|RAM
main.py=MAIN|RAM
```

Use **Push project** to send changed files in manifest order. The plugin checks both its local synchronization state and the live calculator directory, so a program that was deleted or moved on the calculator is restored on the next push.

Use **Pull project** to download every Python program from the calculator and rebuild the local files and manifest. Existing local changes are shown before anything is overwritten.

Calculator names must be unique and contain 1–8 letters or digits. Manifest paths are relative to the project directory.

## TI Python editor support

The bundled type stubs make calculator APIs feel native in PyCharm:

```python
import ti_draw
import ti_plotlib
import ti_system

ti_draw.set_color(255, 0, 0)
ti_draw.fill_circle(160, 104, 24)
ti_plotlib.scatter([1, 2, 3], [2, 4, 8], "o")
key = ti_system.wait_key()
```

The stubs are used by the editor only. They are not a desktop implementation of the TI modules and are never uploaded to the calculator.

## Companion PowerShell CLI

Each GitHub release includes a separate `ti84-evo-cli-<version>.zip` for Windows. Install Java 25, extract the archive, and keep `ti84-evo.ps1` beside `ti84-evo-cli.jar`.

```powershell
.\ti84-evo.ps1 list-files
.\ti84-evo.ps1 send .\main.py
.\ti84-evo.ps1 send --archive .\scripts
.\ti84-evo.ps1 send
.\ti84-evo.ps1 pull --project .
.\ti84-evo.ps1 archive MAIN:15
.\ti84-evo.ps1 delete MAIN:15
.\ti84-evo.ps1 install-context-menu
```

Run `.\ti84-evo.ps1 --help` for all commands and options. With no path, `send` reads `.ti84-evo-project` from the current directory. The optional context-menu installer adds **Send to TI-84 Evo** for Python files and folders for the current Windows user.

## Documentation and support

The [full documentation](https://ti84-evo-for-pycharm.readthedocs.io/en/latest/) includes task-focused user guides, troubleshooting, protocol reference, a glossary, and development and release notes.

- Found a bug or have a feature request? [Open an issue](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/issues).
- Want to see what changed? Read the [changelog](CHANGELOG.md).
- Interested in the protocol? Start with the [protocol documentation](docs/protocol.rst).

When reporting a hardware problem, include your operating system, PyCharm version, plugin version, calculator software version, the action you attempted, and any error shown by the plugin. Do not include device identifiers or other sensitive values.

## Build from source

The project uses Kotlin, the IntelliJ Platform Gradle Plugin, and the included Gradle wrapper. Install JDK 25, then run:

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat buildPlugin --no-daemon
.\gradlew.bat runIde
```

On macOS or Linux, use `./gradlew` instead of `.\gradlew.bat`. The wrapper downloads the configured Gradle and PyCharm development environment; a separate Gradle installation is not required.

Build the standalone CLI archive with:

```powershell
.\gradlew.bat cliDistZip
```

Generated distributions are written to `build/distributions`. See the [development guide](docs/development.rst) for documentation builds and the release process.

The USB and transfer layers are implemented natively in Kotlin. Protocol behavior was cross-checked against the public [Evo-Programming/evo_usb_py](https://github.com/Evo-Programming/evo_usb_py) project; this plugin does not embed or invoke that Python implementation.

## Project status

The current focus is broad physical-device acceptance and dependable round-trip project workflows. Planned work includes variable renaming, editable TI-BASIC programs, and a PyCharm run configuration that can push and launch a selected project.

Automated tests and plugin packaging run in GitHub Actions, but successful CI cannot verify calculator firmware behavior, USB hardware, or every host configuration.

## License

Copyright © 2026 Taylor B. | Inspyre-Softworks. All rights reserved.

No open-source license has been published for this project. See [LICENSE](LICENSE) for details.
