Development
===========

Local checks
------------

Use the included Gradle 9.6.0 wrapper with Java 25. Set ``JAVA_HOME`` only if
JDK 25 is not already selected:

.. code-block:: powershell

   $env:JAVA_HOME = "C:\path\to\jdk-25"
   .\gradlew.bat test --no-daemon
   .\gradlew.bat buildPlugin --no-daemon

On macOS/Linux, use ``./gradlew`` in place of ``.\gradlew.bat``.

The same test and packaging checks run in GitHub Actions for pull requests and
pushes to ``main`` or ``master``. Both the plugin and companion CLI ZIPs are
uploaded as workflow artifacts.

Documentation checks
--------------------

The documentation build scripts check for Python and Graphviz, install missing
dependencies, create an isolated ``.venv-docs`` environment, and produce a
clean local build with warnings treated as errors.

On Windows:

.. code-block:: powershell

   .\scripts\build_docs.ps1

On macOS, Linux, and other Unix-like systems:

.. code-block:: console

   sh scripts/build_docs.sh

Pass ``-Open`` on Windows or ``--open`` on Unix to open the resulting
``docs/_build/html/index.html`` in the default browser. The scripts reuse the
environment on later runs and reinstall Python packages only when
``docs/requirements.txt`` changes or the environment fails its import check.

Release checklist
-----------------

1. Update the version in ``VERSION``.
2. Add an exact ``## <version>`` section with release-note bullets to
   ``CHANGELOG.md``.
3. Run the test, ``buildPlugin``, and ``cliDistZip`` tasks.
4. Push the version change to ``main``. The release workflow verifies both
   distributions, uploads them as workflow artifacts, publishes the plugin to
   JetBrains Marketplace, and publishes a same-version GitHub Release with both
   ZIPs attached. The matching changelog section becomes both the GitHub release
   description and the plugin ``change-notes`` shown under **What's New**.

The repository must define a ``PUBLISH_TOKEN`` Actions secret containing a
JetBrains Marketplace permanent token. ``CERTIFICATE_CHAIN``, ``PRIVATE_KEY``,
and ``PRIVATE_KEY_PASSWORD`` may also be supplied to sign the plugin before
publication. JetBrains requires the first Marketplace listing to be created
manually before Gradle can upload later versions.

Read the Docs uses ``.readthedocs.yaml`` and ``docs/conf.py`` to build
``docs/index.rst`` and treats warnings as errors. A physical TI-84 Evo transfer
must be verified separately with the intended calculator and USB setup.
