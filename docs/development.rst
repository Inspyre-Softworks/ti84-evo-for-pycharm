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
pushes to ``main`` or ``master``. The resulting ZIP is uploaded as a workflow
artifact.

Documentation checks
--------------------

Install the documentation dependencies and build with warnings treated as
errors:

.. code-block:: console

   python -m pip install --requirement docs/requirements.txt
   python -m sphinx -W --keep-going -b html docs docs/_build/html
   python -m sphinx -W --keep-going -b linkcheck docs docs/_build/linkcheck

Release checklist
-----------------

1. Update the version in ``VERSION``.
2. Move the corresponding entry from the snapshot section of
   ``CHANGELOG.md`` into a dated release section.
3. Run the test and ``buildPlugin`` tasks.
4. Inspect the plugin ZIP and publish it through the chosen JetBrains/GitHub
   release process.

Read the Docs uses ``.readthedocs.yaml`` and ``docs/conf.py`` to build
``docs/index.rst`` and treats warnings as errors. A physical TI-84 Evo transfer
must be verified separately with the intended calculator and USB setup.
