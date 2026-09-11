Development
===========

The plugin and companion CLI are implemented in Kotlin and built with the
included Gradle wrapper. Protocol-facing changes should be covered by host-side
tests and then accepted separately on physical hardware.

Development environment
-----------------------

Install JDK 25. The Gradle 9.6.0 wrapper downloads the configured PyCharm
2026.2.1 development sandbox, so a separate Gradle or PyCharm installation is
not required.

The main source areas are:

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Path
     - Purpose
   * - ``src/main/kotlin``
     - Plugin UI, services, project synchronization, transport, and protocol
   * - ``src/main/resources``
     - Plugin metadata, icons, and TI Python type stubs
   * - ``src/cli``
     - Standalone Kotlin CLI and PowerShell launcher
   * - ``src/test/kotlin``
     - Host-side unit and integration-style protocol tests
   * - ``docs`` and ``scripts``
     - Sphinx sources and cross-platform documentation helpers

Local workflow
--------------

.. graphviz::
   :align: center

   digraph dev_flow {
       rankdir=LR;
       graph [bgcolor="transparent", pad=0.15, nodesep=0.28, ranksep=0.4];
       node [shape=box, style="rounded,filled", fillcolor="#f3fbf5",
             color="#269745", fontcolor="#173b22", fontname="Arial",
             fontsize=10, margin="0.16,0.10"];
       edge [color="#52605a", fontcolor="#365141", fontname="Arial",
             fontsize=9, arrowsize=0.7];

       edit [label="Edit"];
       test [label="test"];
       run [label="runIde"];
       package [label="buildPlugin +\ncliDistZip"];
       hardware [label="Physical calculator\nacceptance", fillcolor="#f6f0fd", color="#a161f0"];

       edit -> test -> run -> package -> hardware;
   }

Use the wrapper for your platform:

.. tab-set::

   .. tab-item:: Windows

      .. code-block:: powershell

         .\gradlew.bat test --no-daemon
         .\gradlew.bat runIde
         .\gradlew.bat buildPlugin cliDistZip --no-daemon

   .. tab-item:: macOS / Linux

      .. code-block:: console

         ./gradlew test --no-daemon
         ./gradlew runIde
         ./gradlew buildPlugin cliDistZip --no-daemon

Set ``JAVA_HOME`` only if JDK 25 is not already selected. Generated
distributions are written to ``build/distributions``.

CI also runs plugin-configuration and binary-compatibility verification in
addition to a clean test and package build.

Documentation workflow
----------------------

The documentation helpers require Python 3.10 or newer and Graphviz. They can
install missing dependencies, create an isolated ``.venv-docs`` environment,
and produce a clean Sphinx build with warnings treated as errors.

.. tab-set::

   .. tab-item:: Windows

      .. code-block:: powershell

         .\scripts\build_docs.ps1
         .\scripts\build_docs.ps1 -Open

   .. tab-item:: macOS / Linux

      .. code-block:: console

         sh scripts/build_docs.sh
         sh scripts/build_docs.sh --open

The generated site starts at ``docs/_build/html/index.html``. Dependencies are
reinstalled only when ``docs/requirements.txt`` changes or the environment
fails its import check.

Release workflow
----------------

.. graphviz::
   :align: center

   digraph release_flow {
       rankdir=LR;
       graph [bgcolor="transparent", pad=0.15, nodesep=0.28, ranksep=0.4];
       node [shape=box, style="rounded,filled", fillcolor="#f3fbf5",
             color="#269745", fontcolor="#173b22", fontname="Arial",
             fontsize=10, margin="0.16,0.10"];
       edge [color="#52605a", fontcolor="#365141", fontname="Arial",
             fontsize=9, arrowsize=0.7];

       version [label="Update VERSION"];
       changelog [label="Add matching\nCHANGELOG section"];
       verify [label="Test, package,\nand verify"];
       push [label="Push to main"];
       draft [label="Create/refresh draft\nGitHub release"];
       market [label="Publish to\nMarketplace"];
       release [label="Publish GitHub\nrelease"];

       version -> changelog -> verify -> push -> draft -> market -> release;
   }

Release checklist:

1. Update the semantic version in ``VERSION``.
2. Add an exact ``## <version>`` section with release-note bullets to
   ``CHANGELOG.md``.
3. Run ``test``, ``buildPlugin``, ``cliDistZip``,
   ``verifyPluginProjectConfiguration``, and ``verifyPlugin``.
4. Push the release commit to ``main``.

The release workflow also resumes a missing or draft release for the current
version. It validates both distributions, creates or refreshes a draft GitHub
release, publishes the plugin to JetBrains Marketplace, and then publishes the
GitHub release with both ZIPs. A hyphenated version is marked as a prerelease.
The matching changelog section supplies both the GitHub release notes and the
plugin **What's New** notes.

Repository configuration
~~~~~~~~~~~~~~~~~~~~~~~~

``PUBLISH_TOKEN`` must contain a JetBrains Marketplace permanent token.
``CERTIFICATE_CHAIN``, ``PRIVATE_KEY``, and ``PRIVATE_KEY_PASSWORD`` are
optional signing secrets. JetBrains requires the first Marketplace listing to
be created manually before Gradle can upload later versions.

Read the Docs uses ``.readthedocs.yaml`` and ``docs/conf.py`` and treats Sphinx
warnings as errors.

.. _hardware-status:

Hardware acceptance
-------------------

Host-side tests validate codecs, payloads, state handling, and transaction
logic, but they cannot prove USB timing or calculator firmware behavior.

Release 0.4.2 passed the complete backup, mixed-target project round trip,
calculator-side mutation recovery, editable-variable, Archive, image, and
screenshot matrix on a physical TI-84 Evo running OS ``7.0.0.3996`` from a
Windows 11 host. See :doc:`hardware-acceptance` for the sanitized configuration,
results, firmware-specific behavior, evidence files, and repeatable commands.

This remains evidence for one calculator and host configuration, not every
firmware, platform, cable, or USB controller. Keep backups and record the
calculator software version during future hardware tests.
