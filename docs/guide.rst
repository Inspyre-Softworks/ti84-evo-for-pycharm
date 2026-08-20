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

The tool window supports screen capture, attribute reads, upload of the
current Python file, and ordered multi-file project pushes.

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
