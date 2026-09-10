TI-84 Evo for PyCharm
=====================

Build TI Python projects in PyCharm and transfer them directly to a TI-84 Evo
over USB. The plugin also manages calculator variables and images, captures the
screen, and provides editor support for the built-in ``ti_*`` modules.

The current documentation version is |release|.

.. important::

   This is an alpha hardware-integration project. Keep backups of important
   calculator data. See :ref:`hardware-status` for the currently tested paths.

Start here
----------

.. grid:: 1 1 2 2
   :gutter: 2

   .. grid-item-card:: Install and connect
      :link: guide
      :link-type: doc

      Verify the USB connection and upload your first Python file.

   .. grid-item-card:: Manage calculator files
      :link: calculator-files
      :link-type: doc

      Browse, inspect, edit, archive, delete, upload, and capture.

   .. grid-item-card:: Synchronize a project
      :link: projects
      :link-type: doc

      Configure multi-file mappings, push changes, and pull programs safely.

   .. grid-item-card:: Solve a problem
      :link: troubleshooting
      :link-type: doc

      Diagnose device detection, timeouts, conflicts, and image conversion.

Choose a route
--------------

.. graphviz::
   :align: center

   digraph docs_routes {
       rankdir=LR;
       graph [bgcolor="transparent", pad=0.15, nodesep=0.28, ranksep=0.48];
       node [shape=box, style="rounded,filled", fillcolor="#f3fbf5",
             color="#269745", fontcolor="#173b22", fontname="Arial",
             fontsize=10, margin="0.16,0.10"];
       edge [color="#52605a", fontcolor="#365141", fontname="Arial",
             fontsize=9, arrowsize=0.7];

       goal [shape=diamond, label="What do you\nwant to do?"];
       use [label="Use the plugin"];
       automate [label="Use PowerShell"];
       understand [label="Understand internals"];
       contribute [label="Build or release"];
       start [label="Getting started"];
       files [label="Calculator files"];
       projects [label="Multi-file projects"];
       cli [label="CLI"];
       protocol [label="Protocol overview"];
       development [label="Development"];

       goal -> use;
       use -> start;
       use -> files;
       use -> projects;
       goal -> automate -> cli;
       goal -> understand -> protocol;
       goal -> contribute -> development;
   }

Project links
-------------

* `Source code <https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm>`_
* `Issue tracker <https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/issues>`_
* `JetBrains Marketplace
  <https://plugins.jetbrains.com/plugin/33854-ti-84-evo>`_
* `GitHub releases
  <https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/releases>`_

.. toctree::
   :maxdepth: 2
   :caption: Use the plugin
   :hidden:

   guide
   calculator-files
   projects
   cli
   troubleshooting

.. toctree::
   :maxdepth: 2
   :caption: Reference
   :hidden:

   protocol
   glossary

.. toctree::
   :maxdepth: 2
   :caption: Contribute
   :hidden:

   development
   marauders-lock
