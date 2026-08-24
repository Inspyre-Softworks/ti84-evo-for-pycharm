User guide
==========

Requirements
------------

* PyCharm 2026.2.1 or a compatible IDE with plugin development support
* Java 25 for Gradle and the IntelliJ Platform development instance
* A TI-84 Evo connected over USB when using hardware actions

Running the plugin
------------------

Open the repository as a Gradle project and run ``runIde``. In the
development PyCharm instance, open **View → Tool Windows → TI-84 Evo** and
press **Refresh**. Use **Read Attributes** before attempting a write action.

The tool window supports screen capture, attribute reads, calculator file
browsing, upload of the current Python file, and ordered multi-file project
pushes.

Calculator attributes
---------------------

Press **Read Attributes** to open a grouped device-details dialog. Protocol
keys are translated into friendly labels, byte counts are shown in readable
units, and power and certificate states are explained. Press **Copy
Attributes** to place a Markdown report on the clipboard; the report includes
both the formatted details and the original protocol key/value pairs.

Calculator file browser
-----------------------

Press **Browse calculator files** to read the calculator directory. The
sortable table shows each decoded variable name and type together with its
byte size and RAM or Archive location. This milestone is read-only; download,
delete, and rename actions remain future work.

Multi-file projects
-------------------

Choose **Configure Project…** and select the Python files to transfer. The
plugin writes a ``.ti84-evo-project`` manifest at the project root:

.. code-block:: properties

   lib/drawing.py=DRAW
   lib/state.py=STATE
   main.py=MAIN

Paths are project-relative. Calculator names are unique and limited to one
through eight letters or digits. **Push Project** sends each declared file in
manifest order over one serial connection. Unsaved manifest and editor
documents are included.

The initial upload target is RAM with overwrite enabled. Existing calculator
programs with the same name may therefore be replaced.

Single-file acceptance
----------------------

The single-file path was accepted on a physical TI-84 Evo on August 21, 2026:
a 29-byte Python source was uploaded as ``EVOTEST`` to RAM, the plugin reported
the completed transfer, and a subsequent directory read returned the new
type-15 program. Multi-file project push still requires separate physical
acceptance.
