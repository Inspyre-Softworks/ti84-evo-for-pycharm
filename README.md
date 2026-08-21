# TI-84 Evo for PyCharm

Project identifier: `ti84-evo-for-pycharm`

Native Kotlin IntelliJ Platform plugin for talking directly to the TI-84 Evo over its CDC serial interface.

This repository is an alpha hardware-integration project. Host-side protocol
tests and plugin packaging run in GitHub Actions; successful CI does not replace
acceptance testing on a physical TI-84 Evo.

Single-file Python upload and read-only directory browsing were accepted on a
physical TI-84 Evo on August 21, 2026. Other write paths and future actions
still require their own device acceptance.

Documentation is built with Sphinx and configured for Read the Docs. See the
published documentation for the user guide, protocol overview, and release
checklist.

**Author:** Taylor B. | Inspyre-Softworks.

## Current vertical slice

- Detects the TI-84 Evo by USB VID `0451` / PID `E018`.
- Opens the Evo CDC port directly from the JVM with jSerialComm.
- Implements the solved Evo short/extended frame codec, checksum, D-frame escaping, printable sequence numbers, and the confirmed `S → F → A → D → Z → B` transaction ladder.
- Performs resource GETs using the observed `hh01/get/...` request form.
- Decodes the CBOR returned by `sys/attributes`.
- Reconstructs the solved `sys/screen` resource using the Evo `7E N FF` run encoding.
- Converts the little-endian RGB565 framebuffer to a Java image.
- Adds a **TI-84 Evo** PyCharm tool window with Refresh, Read Attributes, a sortable RAM/Archive file browser, Capture Screen, single-file upload, and multi-file project push actions.
- Uses a native icon toolbar with tooltips, grouped actions, persistent status, and an explicit overflow menu at narrow tool-window widths.
- Bundles typed API stubs for the complete `ti_*` module family: `ti_draw`, `ti_image`, `ti_system`, `ti_plotlib`, `ti_hub`, and `ti_rover`.
- Uploads the active PyCharm `.py` file as an Evo type-15 Python program using the calculator's Kermit variable-transfer endpoint.
- Packages source into the Evo Python AppVar + CBOR representation before transfer; it does not send loose desktop text as though the calculator had a normal filesystem.
- Uses negotiated Kermit long packets for host-to-calculator transfers while preserving the proven read-only resource path for screenshots and attributes.

## Target

This scaffold targets **PyCharm 2026.2.1** and therefore uses a **Java 25** toolchain.

JetBrains' current IntelliJ Platform Gradle Plugin 2.x is used, along with Kotlin 2.4.10.

## Run it

1. Open this directory as a Gradle project in IntelliJ IDEA or PyCharm with plugin-development support.
2. Make sure a JDK 25 toolchain is available.
3. Run the Gradle `runIde` task.
4. In the development PyCharm instance, open **View → Tool Windows → TI-84 Evo**.
5. Plug in the calculator and press **Refresh**.
6. Try **Read Attributes** first, then **Capture Screen**.
7. Press **Browse calculator files** to list variable names, types, sizes, and RAM/Archive locations.
8. Open a `.py` file in the editor and press **Upload Current .py**. Confirm the 1–8 character calculator program name; the first version writes to RAM and overwrites an existing program with the same name.

### Push a multi-file project

1. Press **Configure Project…** and select every `.py` file that belongs on the calculator.
2. The plugin creates a source-controlled `.ti84-evo-project` manifest in the project root and opens it in the editor.
3. Review the generated source-to-calculator-name mappings. Calculator names must be unique and contain 1–8 letters or digits.
4. Press **Push Project** to upload every declared file over one calculator connection.

The manifest is intentionally simple and order-preserving:

```properties
# source path = calculator program name
lib/drawing.py=DRAW
lib/state.py=STATE
main.py=MAIN
```

Paths are relative to the PyCharm project. Both the manifest and declared source files are read from current editor documents, so unsaved edits are included. If a later file fails, the tool window identifies it and lists files that were already uploaded.

You can also run Gradle from a terminal once Gradle 9+ is installed:

```powershell
gradle runIde
```

For a reproducible local build, use the included Gradle wrapper with Java 25:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"
.\gradlew.bat test --no-daemon
.\gradlew.bat buildPlugin --no-daemon
```

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
      │
      ▼
EvoLink
      │
      ▼
EvoTransactionEngine
      │
      ▼
EvoFrameCodec
      │
      ▼
EvoSerialTransport (jSerialComm)
      │
      ▼
TI-84 Evo CDC interface
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
Kermit S / F / A / D... / Z / B
      ↓
hh01/xfr/var?...type=15&memtarget=0&policy=1
      ↓
TI-84 Evo RAM
```

Program names are currently restricted to 1–8 letters or digits. The filename stem is sanitized and offered as the default name.

Project push packages each declared source file as its own type-15 Evo Python variable. It opens the serial transport once, then performs one complete Kermit transfer transaction per file in manifest order.

Protocol behavior for modern Evo file transfer was cross-checked against the public `Evo-Programming/evo_usb_py` implementation. This project contains an independent Kotlin implementation rather than embedding or invoking that Python tool.

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

1. expose RAM vs Archive target selection;
2. add download/delete/rename actions;
3. add a real **TI-84 Evo** run configuration that pushes and launches the selected Python project.
