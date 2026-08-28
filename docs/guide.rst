User guide
==========

Requirements
------------

* JDK 25 for the Gradle build and development IDE runtime
* A TI-84 Evo connected over USB when using hardware actions

The included Gradle 9.6.0 wrapper downloads the configured PyCharm 2026.2.1
development sandbox. A separate Gradle or PyCharm installation is not required.

Running the plugin
------------------

From the repository root, run ``.\gradlew.bat runIde`` on Windows or
``./gradlew runIde`` on macOS/Linux. In the development PyCharm instance, open
**View → Tool Windows → TI-84 Evo** and press **Refresh devices**. Use **Read
attributes** before attempting a write action.

The tool window supports screen capture, attribute reads, calculator file
browsing, upload of the current Python file, and ordered multi-file project
pushes.

Calculator attributes
---------------------

Press **Read attributes** to open a grouped device-details dialog. Protocol
keys are translated into friendly labels, byte counts are shown in readable
units, and power and certificate states are explained. Press **Copy
Attributes** to place a Markdown report on the clipboard; the report includes
both the formatted details and the original protocol key/value pairs.

Calculator file browser
-----------------------

Press **Browse calculator files** to read the calculator directory. The
sortable table shows each decoded variable name and type together with its
byte size and RAM or Archive location.

Deleting calculator files
-------------------------

Select one or more rows in the calculator file table and press **Delete
selected**. The confirmation lists each selected variable's name, type, and RAM
or Archive location. The action cannot be undone. If a multi-file deletion fails
partway through, the table removes the variables already deleted and the output
identifies both the completed deletions and the variable that failed. Download
and rename actions remain future work.

Single-file upload
------------------

Open a ``.py`` file in the editor and press **Upload current Python file**.
Enter a calculator program name containing one through eight letters or digits.
The plugin reads the current editor document, including unsaved changes,
normalizes the calculator name to uppercase, and uploads a type-15 Python
program to the selected RAM or Archive target. Overwrite is enabled, so an
existing program with the same name may be replaced.

Multi-file projects
-------------------

Choose **Configure project** to open the full configuration window. Add and
remove Python files, reorder them, edit calculator names, and choose Archive
per file. The plugin writes a ``.ti84-evo-project`` manifest at the project
root:

.. code-block:: properties

   @always-push-all=false
   lib/drawing.py=DRAW|Archive
   lib/state.py=STATE|RAM
   main.py=MAIN|RAM

Paths are project-relative, and selected files must be inside the project
directory. Calculator names are unique and limited to one through eight letters
or digits. The generated manifest sorts the selected source paths; edit the line
order to change the upload order. The file is intended to be checked into source
control. **Always rebuild / push all files** can be enabled in the configuration
window for calculators that are frequently reset or when one project is sent
to several calculators.

**Push project** fingerprints the configured target, calculator name, and
source, then sends only entries changed since their last successful upload.
Successful files are recorded individually, so retrying after a partial failure
does not resend completed files. The optional always-rebuild setting disables
this filtering. Uploads use one serial connection and the tool window displays
file-count progress. Unsaved manifest and source editor changes are included.
If the calculator cannot be reached, the action shows a clear troubleshooting
pop-up; a partial failure still identifies completed and failed programs.

PowerShell and Explorer sender
------------------------------

Build ``cliDistZip`` to create a companion command-line distribution in
``build/distributions``. Extract it and run:

.. code-block:: powershell

   .\ti84-evo.ps1 send                         # changed manifest entries
   .\ti84-evo.ps1 send --always-rebuild        # every manifest entry
   .\ti84-evo.ps1 send --archive .\scripts     # applicable .py files
   .\ti84-evo.ps1 install-context-menu         # current-user Explorer actions

The colored terminal UI shows an upload plan, connection state, aligned
progress bars, storage targets, and a final summary. The context-menu installer
adds **Send to TI-84 Evo** for ``.py`` files and folders without requiring
administrator access. Run ``uninstall-context-menu`` to remove those entries.
The CLI is packaged separately but remains in this repository so it shares the
same manifest, incremental state, transport, and protocol implementation as the
PyCharm plugin.

TI Python editor support
------------------------

The plugin exposes typed editor stubs for ``ti_draw``, ``ti_image``,
``ti_system``, ``ti_plotlib``, ``ti_hub``, and ``ti_rover``. They provide
import and member completion, parameter hints, quick documentation, and
type-aware inspections. The stubs are an editor-only synthetic library: they
are not installed as a desktop runtime and are never uploaded to the calculator.

Single-file acceptance
----------------------

The single-file path was accepted on a physical TI-84 Evo on August 21, 2026:
a 29-byte Python source was uploaded as ``EVOTEST`` to RAM, the plugin reported
the completed transfer, and a subsequent directory read returned the new
type-15 program. Multi-file project push still requires separate physical
acceptance.
