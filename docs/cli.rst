PowerShell CLI and Explorer integration
=======================================

The companion CLI uses the same project manifests, synchronization state,
transport, and protocol implementation as the PyCharm plugin. It is intended
for Windows PowerShell and can optionally add current-user Explorer actions.

Install
-------

1. Install Java 25.
2. Download ``ti84-evo-cli-<version>.zip`` from the matching `GitHub release
   <https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/releases>`_, or
   build it with ``.\gradlew.bat cliDistZip``.
3. Extract the archive and keep ``ti84-evo.ps1`` beside
   ``ti84-evo-cli.jar``.
4. Open PowerShell in that directory and run ``.\ti84-evo.ps1 --help``.

Command map
-----------

.. graphviz::
   :align: center

   digraph cli_commands {
       rankdir=LR;
       graph [bgcolor="transparent", pad=0.15, nodesep=0.25, ranksep=0.45];
       node [shape=box, style="rounded,filled", fillcolor="#f3fbf5",
             color="#269745", fontcolor="#173b22", fontname="Arial",
             fontsize=10, margin="0.16,0.10"];
       edge [color="#52605a", fontname="Arial", fontsize=9, arrowsize=0.7];

       cli [label="ti84-evo.ps1"];
       read [label="Read", fillcolor="#e8f5ec"];
       sync [label="Synchronize", fillcolor="#f6f0fd", color="#a161f0"];
       mutate [label="Manage variables", fillcolor="#fff4f2", color="#b14b3b"];
       shell [label="Explorer", fillcolor="#eef4fc", color="#477db3"];
       list [label="list-files"];
       send [label="send / pull"];
       vars [label="archive / delete"];
       menus [label="install-context-menu\nuninstall-context-menu"];

       cli -> read -> list;
       cli -> sync -> send;
       cli -> mutate -> vars;
       cli -> shell -> menus;
   }

Common commands
---------------

.. code-block:: powershell

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

``list-files`` is read-only and shows each variable's name, numeric type, size,
and RAM/Archive location.

Send and pull
-------------

With no path, ``send`` reads ``.ti84-evo-project`` from the current directory
and uploads only entries that are not synchronized. A file or directory
argument sends applicable ``.py`` files directly. Use ``--archive`` or
``--ram`` to override the destination, and ``--always-rebuild`` (also
``--all`` or ``--force``) to send every entry.

``pull --project DIR`` reconstructs source files and the project manifest. It
refuses to replace changed local files unless ``--force`` (or ``--overwrite``)
is supplied.

Archive and delete
------------------

``archive`` and ``delete`` accept one or more ``NAME[:TYPE]`` selectors. Add
the numeric type when a name matches more than one calculator entry.

``delete`` prints the exact plan and asks for confirmation. Use ``--yes`` only
for an intentional non-interactive operation; ``rm`` is an alias. Selecting
``L1`` through ``L6`` clears the values and retains the built-in List Editor
slot rather than deleting it.

Explorer context menus
----------------------

Run the following from the extracted distribution directory:

.. code-block:: powershell

   .\ti84-evo.ps1 install-context-menu

This adds **Send to TI-84 Evo** for ``.py`` files and folders for the current
Windows user; administrator access is not required. Remove the entries with:

.. code-block:: powershell

   .\ti84-evo.ps1 uninstall-context-menu

If a command cannot find the calculator, continue with :doc:`troubleshooting`.
