Hardware acceptance
===================

Hardware acceptance is separate from the host-side test suite. It exercises a
physical calculator with contained ``HA42`` fixtures, saves protocol traces,
removes every fixture, and finally compares the calculator directory with its
pre-run identity and RAM/Archive locations.

0.4.2 accepted configuration
----------------------------

Release 0.4.2 passed the complete workflow on 11 September 2026 with this
sanitized configuration:

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Component
     - Accepted configuration
   * - Calculator
     - TI-84 Evo, product ``23-10-28-2100``
   * - OS package
     - ``7.0.0.3996``
   * - BSP / BL2 / BL1
     - ``7.0.0.3992`` / ``7.0.0.3386`` / ``1.0.0.260``
   * - Host
     - Windows 11 10.0, amd64
   * - Java
     - Oracle JDK ``25.0.4.1``
   * - USB transport
     - TI VID ``0451``, PID ``E018``, Windows CDC serial

The passing run took 195 seconds and covered:

* a complete 32-variable preflight backup with SHA-256 manifest;
* a three-file Python project pushed to mixed RAM and Archive targets;
* calculator-side program modification, deletion, and RAM/Archive movement,
  followed by a normal incremental push;
* a pull into a clean directory and byte-for-byte source comparison;
* create, edit, read back, move to Archive, and delete for a number, custom
  list, and matrix;
* IM8C conversion, upload, readback, Archive placement, and deletion;
* two full-resolution ``320 x 240`` RGB565 screen captures saved as PNG; and
* fixture cleanup followed by an exact match with all original variable
  identities and memory locations.

Firmware 7.0 reports two behaviors that 0.4.2 handles explicitly. A committed
native number upload can return ``DP`` for its terminal B packet, so the client
reads the value and location back before deciding whether it failed. Type-8
Python image AppVars are rejected in RAM and are retried in Archive.

Run the acceptance harness
--------------------------

Build the CLI, connect the calculator, and choose a new evidence directory.
The backup must finish before the mutating harness will start:

.. code-block:: powershell

   .\gradlew.bat cliJar --no-daemon
   java --enable-native-access=ALL-UNNAMED -jar build\libs\ti84-evo-cli.jar backup captures\0.4.2-hardware-acceptance\backup
   java --enable-native-access=ALL-UNNAMED -jar build\libs\ti84-evo-cli.jar hardware-acceptance captures\0.4.2-hardware-acceptance\run --backup captures\0.4.2-hardware-acceptance\backup

``backup`` writes native variable files, the calculator directory, sanitized
attributes, SHA-256 hashes, and a ``COMPLETE.txt`` marker. The acceptance run
writes ``host-configuration.txt``, ``results.md``, source-comparison fixtures,
screenshots, and length-prefixed ``tx-packets.bin`` / ``rx-packets.bin`` files
with a digest-only ``protocol.log``. Device identifiers are redacted from the
host record. Treat raw protocol captures as private diagnostic artifacts and
review them before sharing.

One passing calculator and host do not establish compatibility with every
firmware, platform, cable, or USB controller. Repeat this harness for new
firmware and materially different host configurations.
