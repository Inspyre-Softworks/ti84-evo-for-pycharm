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
program to RAM. Overwrite is enabled, so an existing program with the same
name may be replaced.

Multi-file projects
-------------------

Choose **Configure project** and select the Python files to transfer. The
plugin writes a ``.ti84-evo-project`` manifest at the project root:

.. code-block:: properties

   lib/drawing.py=DRAW
   lib/state.py=STATE
   main.py=MAIN

Paths are project-relative, and selected files must be inside the project
directory. Calculator names are unique and limited to one through eight letters
or digits. The generated manifest sorts the selected source paths; edit the line
order to change the upload order. The file is intended to be checked into source
control, and the plugin asks before replacing an existing manifest.

**Push project** sends each declared file in manifest order over one serial
connection, using one complete transfer transaction per file. Unsaved manifest
and source editor changes are included. If a later upload fails, the tool window
identifies the failed program and lists the programs already uploaded. Project
uploads also target RAM with overwrite enabled.

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
