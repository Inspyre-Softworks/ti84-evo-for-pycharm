Development
===========

Local checks
------------

Use the Gradle wrapper with Java 25:

.. code-block:: powershell

   $env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"
   .\gradlew.bat test --no-daemon
   .\gradlew.bat buildPlugin --no-daemon

The same test and packaging checks run in GitHub Actions. The resulting ZIP
is uploaded as a workflow artifact for pull requests and branch pushes.

Release checklist
-----------------

1. Update the version in ``build.gradle.kts``.
2. Move the corresponding entry from the snapshot section of
   ``CHANGELOG.md`` into a dated release section.
3. Run the test and ``buildPlugin`` tasks.
4. Inspect the plugin ZIP and publish it through the chosen JetBrains/GitHub
   release process.

Read the Docs builds ``docs/index.rst`` from ``main`` and treats warnings as
errors. A physical TI-84 Evo transfer must be verified separately with the
intended calculator and USB setup.
