# TI-84 Evo for PyCharm

[![Documentation Status](https://readthedocs.org/projects/ti84-evo-for-pycharm/badge/?version=latest)](https://ti84-evo-for-pycharm.readthedocs.io/en/latest/?badge=latest)
[![Build](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/actions/workflows/ci.yml/badge.svg)](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/actions/workflows/ci.yml)
[![Sourcery](https://img.shields.io/badge/Sourcery-enabled-brightgreen)](https://sourcery.ai)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/33854?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/33854-ti-84-evo)

The project version is defined once in [`VERSION`](VERSION). Gradle uses it for
plugin packaging and generated plugin metadata; the documentation reads the
same file.

JetBrains plugin ID: `com.inspyresoftworks.ti84evo`

Repository and Gradle project name: `ti84-evo-for-pycharm`

Native Kotlin IntelliJ Platform plugin for talking directly to the TI-84 Evo over its CDC serial interface.

This repository is an alpha hardware-integration project. Host-side protocol
tests and plugin packaging run in GitHub Actions; successful CI does not replace
acceptance testing on a physical TI-84 Evo.

Single-file Python upload and read-only directory browsing were accepted on a
physical TI-84 Evo on August 21, 2026. Other write paths and future actions
still require their own device acceptance.

Documentation is built with Sphinx and published on
[Read the Docs](https://ti84-evo-for-pycharm.readthedocs.io/en/latest/). The
published site includes the user guide, protocol overview, glossary, and
development/release checklist. The documentation source is in [`docs/`](docs/). The
[source code](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm) and
[issue tracker](https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/issues)
are on GitHub.

**Author:** Taylor B. | Inspyre-Softworks.

## Current vertical slice

- Detects the TI-84 Evo by USB VID `0451` / PID `E018`.
- Opens the Evo CDC port directly from the JVM with jSerialComm.
- Implements confirmed Kermit short/extended packets, negotiated checksums, quoting, modulo-64 sequences, and the `S → F → A → D → Z → B` transaction ladder.
- Performs resource GETs using the observed `hh01/get/...` request form.
- Decodes the CBOR returned by `sys/attributes` and presents it in a grouped details dialog with Markdown copy support.
- Reconstructs the solved `sys/screen` resource using the Evo `7E N FF` run encoding.
- Converts the little-endian RGB565 framebuffer to a Java image.
- Saves full-resolution screen captures as PNG through Screen-pane buttons or the
  screenshot's right-click menu, including a one-click project-directory target.
- Adds a **TI-84 Evo** PyCharm tool window with Refresh devices, Read attributes,
  a sortable RAM/Archive file browser with confirmation-protected Archive and
  deletion actions, Capture screen, single-file upload, and project push/pull actions.
- Views and exports every calculator variable in its native Evo representation,
  and creates or replaces numbers, lists, and matrices through the Evo ASCII importer.
- Converts PNG, JPEG, GIF, and BMP images to compressed Evo Python image variables;
  user-level transfer settings control maximum dimensions and palette size.
- Sends existing `.8ci2`, `.8ca2`, and `.8xv2` picture files without conversion.
- Uses a native icon toolbar with tooltips, grouped actions, persistent status, and an explicit overflow menu at narrow tool-window widths.
- Packages dedicated 40×40 light and dark SVG logos for the IDE plugin manager and JetBrains Marketplace.
- Bundles typed API stubs for the complete `ti_*` module family: `ti_draw`, `ti_image`, `ti_system`, `ti_plotlib`, `ti_hub`, and `ti_rover`.
- Uploads the active PyCharm `.py` file as an Evo type-15 Python program to RAM or Archive using the calculator's Kermit variable-transfer endpoint.
- Provides a full project configuration window with editable names, ordering,
  per-file RAM/Archive targets, and an **Always rebuild / push all files** option.
- Pushes only files changed since their last successful upload, with visible
  multi-file progress and clear calculator-connection failure pop-ups.
- Pulls all type-15 Python programs back into local `.py` files, preserves
  Archive targets in the manifest, and requires confirmation before overwriting local changes.
- Packages a companion colored PowerShell CLI with optional current-user
  Explorer context menus for sending Python files, folders, or manifests.
- Lists calculator files from the CLI with their native type IDs, sizes, and
  RAM or Archive locations.
- Pulls Python projects and saves or deletes selected calculator variables from the CLI;
  deleting built-in lists L1–L6 clears their contents and restores the default List Editor columns.
- Packages source into the Evo Python AppVar + CBOR representation before transfer; it does not send loose desktop text as though the calculator had a normal filesystem.
- Uses negotiated Kermit transfers for uploads while retaining the calculator-tested framing and AUX-byte compatibility needed by downloads, screenshots, and attributes.

## Target

The development sandbox targets **PyCharm 2026.2.1**, and the Gradle build uses
a **Java 25** toolchain.

The build uses the IntelliJ Platform Gradle Plugin 2.18.1, Kotlin 2.4.10, and
the included Gradle 9.6.0 wrapper.

## Run from source

1. Install JDK 25 and make it available through `JAVA_HOME` or on `PATH`.
2. From the repository root, run `.\gradlew.bat runIde` on Windows or
   `./gradlew runIde` on macOS/Linux. The wrapper downloads the configured
   Gradle and PyCharm versions; a separate Gradle or PyCharm installation is
   not required.
3. In the development PyCharm instance, open **View → Tool Windows → TI-84 Evo**.
4. Plug in the calculator and press **Refresh devices**.
5. Try **Read attributes** first, then **Capture screen**.
   Use the buttons below the capture—or right-click it—to save a PNG to any path
   or directly into the project directory.
6. Press **Browse calculator files** to list variable names, types, sizes, and RAM/Archive locations.
7. Select one or more rows in the calculator file table and press **Delete
   selected**. Confirm the exact RAM or Archive files before deletion. For
   built-in lists L1–L6, this clears the values and restores the default L1–L6
   List Editor columns without changing the other lists' values.
   To preserve RAM variables instead, press **Save to Archive** and confirm the transfer.
8. Open a `.py` file in the editor and press **Upload current Python file**.
   Confirm the 1–8 character calculator program name and choose RAM or Archive.
   The current implementation overwrites an existing program with the same name.
9. Use **Upload picture** for a desktop image or an existing Evo picture file.
   **Transfer settings** stores image limits and the Marketplace validation interval
   globally for PyCharm, not in the project. Validation defaults to once per hour.
10. In **Calculator Files**, select a row and press **View / edit** to inspect or
    export it. Native numbers, lists, and matrices are decoded, and **Replace Value**
    opens their current contents in a pre-populated editor. **Add variable** creates a
    new value of one of those types.

### Push a multi-file project

1. Press **Configure project** and add every `.py` file that belongs on the calculator.
2. The plugin creates a project-local `.ti84-evo-project` manifest, intended to
   be checked into source control, and opens it in the editor. It asks before
   replacing an existing manifest.
3. Review the generated source-to-calculator-name mappings. Calculator names must be unique and contain 1–8 letters or digits.
4. Press **Push project** to upload every declared file over one calculator connection.
5. Press **Pull project** to download every Python program from the calculator.
   Existing manifest mappings are reused; local conflicts are listed before any overwrite.

The manifest is intentionally simple and order-preserving:

```properties
# source path = calculator program name | RAM or Archive
@always-push-all=false
lib/drawing.py=DRAW|Archive
lib/state.py=STATE|RAM
main.py=MAIN|RAM
```

Paths are relative to the PyCharm project, and selected files must be inside the
project directory. Normal pushes send only changed files; enable **Always
rebuild / push all files** in the configuration window to override that. Both
the manifest and declared source files are read from current editor documents,
so unsaved edits are included. If a later file fails, the tool window identifies
it and records files that were already uploaded so a retry skips them.

### PowerShell and Explorer

Install Java 25, download and extract `ti84-evo-cli-<version>.zip` from the
matching GitHub release, then open PowerShell in the extracted folder. Keep
`ti84-evo.ps1` next to `ti84-evo-cli.jar` and run:

```powershell
.\ti84-evo.ps1 --help
.\ti84-evo.ps1 list-files
.\ti84-evo.ps1 send .\main.py
.\ti84-evo.ps1 send --archive .\scripts
.\ti84-evo.ps1 send
.\ti84-evo.ps1 send --always-rebuild
.\ti84-evo.ps1 pull --project .
.\ti84-evo.ps1 pull --project . --force
.\ti84-evo.ps1 archive MAIN:15
.\ti84-evo.ps1 delete MAIN:15
.\ti84-evo.ps1 delete L1:1
.\ti84-evo.ps1 delete --yes TEMP:15 OLDLIST:1
.\ti84-evo.ps1 install-context-menu
```

With no path, `send` reads the `.ti84-evo-project` manifest in the current
folder. A file or folder path sends the applicable Python files directly;
`--archive` or `--ram` overrides their destination. `pull` reconstructs local
Python files and the manifest, refusing changed local files unless `--force` is
provided. Incremental sends check both local fingerprints and the live calculator
directory, so a configured program deleted from the calculator is restored even
when its source has not changed. `archive` and `delete` accept one or more `NAME` or `NAME:TYPE`
selectors. Deletion lists the exact variables and asks for confirmation; use
`--yes` only for an intentional non-interactive deletion. Built-in lists L1–L6
are cleared and retained; custom lists are removed. To build the ZIP locally,
run `.\gradlew.bat cliDistZip` and extract the result from
`build\distributions`.

The CLI stays in this repository as a separate artifact, sharing the plugin's
protocol, manifest, Archive support, and incremental upload state. The optional
installer adds **Send to TI-84 Evo** to `.py` file and folder context menus for
the current Windows user.

For a reproducible local build, use the included Gradle wrapper with Java 25.
Set `JAVA_HOME` only if JDK 25 is not already selected:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
.\gradlew.bat test --no-daemon
.\gradlew.bat buildPlugin --no-daemon
```

On macOS/Linux, use `./gradlew` in place of `.\gradlew.bat`.

On Windows, if the checkout is inside OneDrive, generated Gradle output is
automatically redirected to `%LOCALAPPDATA%\ti84-evo-for-pycharm\build` so
OneDrive cannot replace compiler directories with cloud placeholders. Set
`TI84_EVO_BUILD_DIR` or pass `-Pti84EvoBuildDir=...` to choose another location.

## Architecture

```text
PyCharm tool window
        │
        ▼
EvoDeviceService
├─ resource operations and project pulls
│  └─ EvoLink
│     └─ EvoTransactionEngine
│        └─ KermitPacketCodec / EvoResourceCodec
│           └─ EvoSerialTransport (jSerialComm)
│              └─ TI-84 Evo CDC interface
└─ Python transfers
   └─ EvoPythonTransfer
      └─ EvoPythonPayload / KermitPacketCodec
         └─ EvoSerialTransport (jSerialComm)
            └─ TI-84 Evo CDC interface
```

The protocol layer is deliberately independent of the UI so the same Kotlin implementation can later back run configurations, file upload/download, a calculator file browser, and other JetBrains IDE integrations.

## Python upload path

The upload action performs this pipeline:

```text
active editor .py
      ↓
UTF-8 source
      ↓
Evo Python AppVar (type 15)
      ↓
CBOR variable-transfer payload
      ↓
Kermit S / F / A / D… / Z / B
      ↓
hh01/xfr/var?...type=15&memtarget=0&policy=1
      ↓
TI-84 Evo RAM or Archive
```

Program names are currently restricted to 1–8 letters or digits. The filename stem is sanitized and offered as the default name.

Project push packages each declared source file as its own type-15 Evo Python variable. It opens the serial transport once, then performs one complete Kermit transfer transaction per file in manifest order.

Protocol behavior for modern Evo file transfer was cross-checked against the
public [Evo-Programming/evo_usb_py](https://github.com/Evo-Programming/evo_usb_py)
implementation. This project contains an independent Kotlin implementation
rather than embedding or invoking that Python tool.

## TI Python completion

The plugin exposes the calculator-provided `ti_draw`, `ti_image`, `ti_system`, `ti_plotlib`, `ti_hub`, and `ti_rover` modules as a synthetic PyCharm library. No desktop runtime package is installed and the stubs are never uploaded to the calculator; they only describe the firmware APIs to the editor. This enables module-name completion, `from … import …` symbol completion, member completion, parameter hints, quick documentation, and type-aware inspections for code such as:

```python
import ti_draw
import ti_plotlib
import ti_system

ti_draw.set_color(255, 0, 0)
ti_draw.fill_circle(160, 104, 24)
ti_plotlib.scatter([1, 2, 3], [2, 4, 8], "o")
key = ti_system.wait_key()
```

## Next milestone

1. add calculator-variable rename actions and editable TI-BASIC program support;
2. add a real **TI-84 Evo** run configuration that pushes and launches the selected Python project;
3. perform physical-device acceptance of Archive-save, project-pull, incremental multi-file, and CLI paths.
