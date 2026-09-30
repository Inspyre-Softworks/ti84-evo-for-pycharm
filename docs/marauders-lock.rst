Marauders Lock
==============

:term:`Marauders Lock` is the developer switch that unlocks **Mischief Mode**. It adds
diagnostic controls for Marketplace validation; it does not lock the
calculator, protect files, or change calculator-transfer behavior.

.. warning::

   Mischief Mode is intended for plugin development and troubleshooting. Its
   one-second Marketplace check interval can create unnecessary network traffic.
   Leave the normal one-hour interval in place unless you are actively testing
   version validation.

What it enables
---------------

While Mischief Mode is active:

* **Transfer settings** permits a Marketplace check interval as low as one
  second instead of the normal 60-second minimum.
* Marketplace validation writes verbose diagnostic messages to the PyCharm log.
* **About TI-84 Evo** adds **Submit Issue/Bug** and **Open Installed Plugin
  Directory** actions, plus **Always Show Changelog**. The checkbox also
  appears in the changelog window. When selected, the release notes appear
  once per IDE session when the TI-84 Evo tool window opens while Mischief Mode
  is active. Without it, notes appear once per installed plugin version.
* **Copy Debug Info** reports that Mischief Mode is enabled.
* The TI-84 Evo toolbar shows **Developer utilities**. Its separate window
  launches the bundled 15-second benchmark, continuous screen preview, MP4
  screen recorder, :term:`SmartPad` key observer, macro
  pad, and USB diagnostic. It displays command output and has a **Stop** button.
  Only one utility runs from that window at a time. The screen benchmark uses
  the calculator serial port; the SmartPad tools use Windows keyboard or USB
  access. Stop or close the preview to finish a recording; MP4 encoding may
  continue for a while after capture ends. Closing the utilities window also
  stops its active utility.
* Closing or restarting PyCharm asks whether the mischief has been managed.

The setting is application-wide for the current operating-system user, so it
affects every PyCharm project opened by that user.

Developer utility requirements
------------------------------

The **15-second benchmark**, **Live screen preview**, and **Record live screen
(MP4)** buttons use the plugin's bundled Java runtime and the calculator's USB
serial connection. Close other software that owns the serial port. Recording
saves PNG frames and timing data before finalizing the MP4; see :doc:`development`
for the standalone probe and recovery command.

**SmartPad key observer** and **SmartPad macro pad** require Windows, OS 7.1
SmartPad mode on the calculator, and a Python installation available through
the ``py`` launcher. They use Windows global hotkeys and do not require
:term:`USBPcap` or the serial port. **Run USB diagnostic** also launches Python. Its
raw USB capture workflow requires USBPcap, usually installed with Wireshark,
and the optional Python packages listed in :doc:`development`. See
:doc:`smartpad` for the key mappings, capture commands, and limitations.

Activate the lock
-----------------

Create a plain UTF-8 file named ``mm.lock`` directly in your user home
directory. Its entire contents must be this case-sensitive phrase:

.. code-block:: text

   I solemnly swear that I am up to no good.

One final LF or CRLF newline is allowed. Extra spaces, different capitalization,
or more than one trailing newline prevent activation.

.. tab-set::

   .. tab-item:: Windows PowerShell

      .. code-block:: powershell

         $lockPath = Join-Path $env:USERPROFILE "mm.lock"
         Set-Content -LiteralPath $lockPath `
             -Value "I solemnly swear that I am up to no good." `
             -NoNewline -Encoding ascii

   .. tab-item:: macOS / Linux

      .. code-block:: console

         printf '%s' 'I solemnly swear that I am up to no good.' > "$HOME/mm.lock"

After creating the file, open **About TI-84 Evo** and click **Re-check
Marauders Lock**. This control stays in a separate row at the bottom of About,
even when the lock is inactive. It enables developer controls immediately when
the file contains the exact phrase. Use **Copy Debug Info** to confirm that the
report contains ``Mischief Mode: Enabled``.

Lifecycle
---------

.. graphviz::
   :align: center

   digraph marauders_lock {
       rankdir=LR;
       graph [bgcolor="transparent", pad=0.15, nodesep=0.28, ranksep=0.45];
       node [shape=box, style="rounded,filled", fillcolor="#f3fbf5",
             color="#269745", fontcolor="#173b22", fontname="Arial",
             fontsize=10, margin="0.16,0.10"];
       edge [color="#52605a", fontcolor="#365141", fontname="Arial",
             fontsize=9, arrowsize=0.7];

       absent [label="mm.lock absent or\ncontents do not match"];
       phrase [label="Write exact\nactivation phrase", fillcolor="#f6f0fd", color="#a161f0"];
       active [label="Mischief Mode\nactive", fillcolor="#fff4f2", color="#b14b3b"];
       close [shape=diamond, label="Mischief\nmanaged?"];
       managed [label="Write managed phrase\nto mm.lock", fillcolor="#e8f5ec"];

       absent -> phrase -> active -> close;
       close -> active [label="No"];
       close -> managed [label="Yes"];
       managed -> absent;
   }

Deactivate the lock
-------------------

When PyCharm closes or restarts while the exact activation phrase is present,
it asks **Mischief managed?**

* Choose **Yes** to replace the file contents with ``Mischief managed!``. The
  file remains in place, but Mischief Mode is no longer active.
* Choose **No** to leave the activation phrase unchanged and keep the mode
  active.

You can also deactivate it manually by deleting ``mm.lock`` or changing its
contents. Restart PyCharm afterward. Lower Marketplace intervals already saved
in the settings are retained, but normal mode clamps the effective interval to
at least 60 seconds.

Troubleshooting
---------------

If the mode does not activate, verify that:

* the filename is exactly ``mm.lock`` rather than ``mm.lock.txt``;
* the file is directly under the Java user-home directory;
* the phrase has exact capitalization and punctuation; and
* there is no byte-order mark, leading whitespace, trailing space, or extra
  blank line.

Use :doc:`troubleshooting` for calculator connectivity problems; Marauders Lock
does not alter USB detection or protocol behavior.
