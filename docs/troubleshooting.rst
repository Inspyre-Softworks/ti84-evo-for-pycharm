Troubleshooting
===============

Start with the symptom below. The plugin automatically detects the TI-84 Evo
USB interface; there is no manual port selector.

Calculator not detected
-----------------------

The plugin expects exactly one USB :term:`CDC` device with Texas Instruments
:term:`VID` ``0451`` and TI-84 Evo :term:`PID` ``E018``.

1. Wake the calculator and reconnect both ends of the cable.
2. Try a known USB data cable and a different USB port. Avoid an unpowered hub.
3. Close other calculator-link or serial-port software.
4. Select **Refresh devices** again.
5. Disconnect extra TI-84 Evo calculators if more than one is attached.

Detected, but an operation times out
------------------------------------

1. Keep the calculator awake and on its home screen.
2. Close software that may have opened the same serial port.
3. Reconnect the calculator, select **Refresh devices**, and use **Read
   attributes** as a read-only connection test.
4. Retry the original action once.

For a partial project, :term:`Archive`, delete, or pull failure, read the operation
output before retrying. Completed items are recorded individually and are
identified in the failure details.

Upload action is unavailable or rejected
-----------------------------------------

For **Upload current Python file**, make sure a ``.py`` file is active in the
editor. Calculator Python names must contain one to eight letters or digits;
the plugin converts them to uppercase.

For **Push project**, confirm that ``.ti84-evo-project`` exists at the project
root, every declared path stays inside that root, and every source file exists.
Calculator names in one manifest must be unique.

Pull reports local conflicts
----------------------------

This is overwrite protection, not a connection failure. The pull preview lists
local files whose contents differ from the calculator programs.

* Choose **Cancel** to keep every local file unchanged.
* Back up or commit local work, then choose **Overwrite and Pull** if the
  calculator copy should replace it.
* In the CLI, rerun with ``--force`` only after reviewing the reported paths.

Picture conversion fails
------------------------

Use PNG, JPEG, GIF, or BMP for conversion, or provide a native ``.8ci2``,
``.8ca2``, or ``.8xv2`` file. If a large image cannot be encoded, enable image
reduction under **Transfer settings** or lower its maximum dimensions or color
count.

SmartPad diagnostic problems
----------------------------

The production plugin does not bind calculator keys to IDE actions. Release
0.6.0 includes an optional Windows macro pad that registers selected :term:`SmartPad`
key chords as global hotkeys. It can be run from a source checkout as described
in :doc:`smartpad`, or opened through **Developer utilities** in the installed
plugin after enabling :doc:`marauders-lock`.

If the :term:`HID` interface is listed but no raw reports arrive, confirm that the
SmartPad application is actually open. OS 7.1 enumerates the HID boot-keyboard
interface even on the ordinary home screen, so interface presence alone does
not mean SmartPad is active.

On Windows, the operating-system keyboard driver may prevent hidapi from
opening the interface. Use ``usbpcap-monitor`` with :term:`USBPcap`/Wireshark instead;
it records raw USB endpoint ``84`` reports without replacing the keyboard driver.
If no USBPcap capture root is available, rerun the Wireshark installer and
enable its USBPcap component.

Calculator keys can also produce text or shortcuts in the focused Windows
application because the device is a standard keyboard. Focus a disposable text
field while testing and close SmartPad when finished. If using the optional
macro pad, stop it when finished; global hotkeys match key chords rather than
the physical calculator, so another keyboard can trigger a mapping. Never
publish an unfiltered root-bus capture: it can include
traffic from unrelated USB devices. The supplied capture command retains only
the calculator's device address in its requested output file.

Collect useful debug information
--------------------------------

Open **About TI-84 Evo** from the tool window and choose **Copy Debug Info**.
The report is sanitized Markdown and includes the plugin/version-validation
state and relevant runtime details.

When `opening an issue
<https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm/issues>`_, include:

* operating system and version;
* PyCharm and plugin versions;
* calculator software version;
* the action you attempted;
* the exact error message; and
* the copied debug information, after reviewing it.

Do not include device identifiers or other sensitive values. If the problem is
a protocol or hardware-compatibility issue, note that automated tests cannot
reproduce the physical USB and calculator-firmware environment.
