User guide
==========

Requirements
------------

* JDK 25 for the Gradle build and development IDE runtime
* A TI-84 Evo connected over USB when using hardware actions

The included Gradle 9.6.0 wrapper downloads the configured PyCharm 2026.2.1
development sandbox. A separate Gradle or PyCharm installation is not required.

Running the plugin
------------------

From the repository root, run ``.\gradlew.bat runIde`` on Windows or
``./gradlew runIde`` on macOS/Linux. In the development PyCharm instance, open
**View → Tool Windows → TI-84 Evo** and press **Refresh devices**. Use **Read
attributes** before attempting a write action.

The tool window supports screen capture, attribute reads, calculator file
browsing, upload of the current Python file, and ordered multi-file project
pushes.

Calculator attributes
---------------------

Press **Read attributes** to open a grouped device-details dialog. Protocol
keys are translated into friendly labels, byte counts are shown in readable
units, and power and certificate states are explained. Press **Copy
Attributes** to place a Markdown report on the clipboard; the report includes
both the formatted details and the original protocol key/value pairs.

Screen capture
--------------

Press **Capture screen** to display the calculator framebuffer in the **Screen**
tab. The two buttons beneath the image save the original full-resolution capture
as a PNG: **Save As…** opens a destination chooser, while **Save to Project Dir**
uses a timestamped filename in the open project's root directory. Right-click
the screenshot for the same two actions. Existing files selected through
**Save As…** require confirmation before replacement; project-directory saves
choose a numbered suffix instead of overwriting an existing capture.

Calculator file browser
-----------------------

Press **Browse calculator files** to read the calculator directory. The
sortable table shows each decoded variable name and type together with its
byte size and RAM or Archive location. Opening or clicking the **Calculator
Files** tab reads a fresh directory. Successful variable, picture, Python,
project, and Archive uploads also refresh the table automatically while
preserving any selection whose calculator identity still exists.

Deleting calculator files
-------------------------

Select one or more rows in the calculator file table and press **Delete
selected**. The confirmation lists each selected variable's name, type, and RAM
or Archive location. For the calculator's built-in ``L1`` through ``L6`` list
slots, this action clears every value but keeps the empty list registered in the
List Editor. The calculator's default L1–L6 column layout is restored after a
built-in list is added, replaced, or cleared. Custom lists and all other
selected variables are removed. Cleared
or deleted data cannot be recovered. If a multi-file operation fails partway
through, the table removes only variables confirmed absent from a fresh
calculator directory and the output identifies completed clears/deletions and
the variable that failed.

The standalone CLI provides the same operation with ``delete NAME[:TYPE]``.
It prints the exact deletion plan and requires interactive confirmation. Pass
``--yes`` for an intentional non-interactive invocation; ``rm`` is an alias.
The plan and result label built-in list operations as ``CLEAR``/``CLEARED``.

Saving calculator files to Archive
----------------------------------

Select one or more RAM rows and press **Save to Archive**. The plugin downloads
each native variable envelope, restores its transfer checksum, re-saves it with
the Archive target, and verifies that the calculator directory reports the new
location. Completed variables remain marked as Archive if a later selection
fails.

Select one row and press **View / edit** to read its complete native Evo variable
envelope. The viewer can save a lossless calculator file for every variable type.
Native numbers, lists, and matrices are decoded to real, fraction, complex, list,
or matrix text. Their **Replace Value** action opens a pre-populated editor;
**Add variable** creates new values of those three types using comma/space-separated
input. Existing lists are replaced through a native type-1 payload so their
columns remain registered in the calculator's List Editor. Other formats remain
available for raw inspection and export while their
editable binary formats continue to be documented.

Picture upload
--------------

**Upload picture** accepts PNG, JPEG, GIF, and BMP images and converts them to a
palette-based, run-length-compressed Evo Python image variable. The variable can be
loaded with ``ti_image.load_image(name)``. Existing ``.8ci2``, ``.8ca2``, and ``.8xv2``
files are sent unchanged.

The **Transfer settings** window controls whether conversion reduces images, the
maximum width and height, and the maximum palette size. These preferences are saved
in PyCharm's user configuration and apply across projects. It also controls the
Marketplace validation interval in seconds; the normal minimum is 60 seconds and the
default is 3600 seconds (one hour).

Single-file upload
------------------

Open a ``.py`` file in the editor and press **Upload current Python file**.
Enter a calculator program name containing one through eight letters or digits.
The plugin reads the current editor document, including unsaved changes,
normalizes the calculator name to uppercase, and uploads a type-15 Python
program to the selected RAM or Archive target. Overwrite is enabled, so an
existing program with the same name may be replaced.

Multi-file projects
-------------------

Choose **Configure project** to open the full configuration window. Add and
remove Python files, reorder them, edit calculator names, and choose Archive
per file. The plugin writes a ``.ti84-evo-project`` manifest at the project
root:

.. code-block:: properties

   @always-push-all=false
   lib/drawing.py=DRAW|Archive
   lib/state.py=STATE|RAM
   main.py=MAIN|RAM

Paths are project-relative, and selected files must be inside the project
directory. Calculator names are unique and limited to one through eight letters
or digits. The generated manifest sorts the selected source paths; edit the line
order to change the upload order. The file is intended to be checked into source
control. **Always rebuild / push all files** can be enabled in the configuration
window for calculators that are frequently reset or when one project is sent
to several calculators.

**Push project** fingerprints the configured target, calculator name, and
source, then reads the live calculator directory. An entry is skipped only when
its fingerprint is unchanged and a type-15 program with the configured name and
RAM/Archive target is present. Deleting or moving a project program on the
calculator therefore makes that entry pending even when its local source has not
changed. Successful files are recorded individually, so retrying after a partial
failure does not resend completed files that remain present. The optional
always-rebuild setting disables this filtering. Uploads use one serial connection
and the tool window displays file-count progress. Unsaved manifest and source
editor changes are included. If the calculator cannot be reached, the action
shows a clear troubleshooting pop-up; a partial failure still identifies
completed and failed programs.

**Pull project** downloads every type-15 Python program, decodes its AppVar
source as UTF-8, and rebuilds the local source files and project manifest.
Existing manifest paths are reused by calculator program name, while new
programs receive deterministic lowercase ``.py`` filenames. RAM/Archive targets
are preserved. The confirmation identifies local files whose contents differ;
those files are not overwritten unless the user explicitly chooses
**Overwrite and Pull**. Pulled files are recorded as synchronized so a normal
push does not immediately resend them.

PowerShell and Explorer sender
------------------------------

Install Java 25, then download the CLI ZIP from the matching GitHub release or
build ``cliDistZip`` to create it in ``build/distributions``. Extract the ZIP,
keep ``ti84-evo.ps1`` beside ``ti84-evo-cli.jar``, and run from that folder:

.. code-block:: powershell

   .\ti84-evo.ps1 --help
   .\ti84-evo.ps1 list-files                       # names, types, sizes, and memory
   .\ti84-evo.ps1 send .\main.py               # one Python file
   .\ti84-evo.ps1 send                         # changed manifest entries
   .\ti84-evo.ps1 send --always-rebuild        # every manifest entry
   .\ti84-evo.ps1 send --archive .\scripts     # applicable .py files
   .\ti84-evo.ps1 pull --project .              # all calculator Python programs
   .\ti84-evo.ps1 pull --project . --force      # permit local-file replacement
   .\ti84-evo.ps1 archive MAIN:15               # save a variable to Archive
   .\ti84-evo.ps1 delete MAIN:15                # confirm and delete a variable
   .\ti84-evo.ps1 delete L1:1                   # clear L1 but keep its list slot
   .\ti84-evo.ps1 delete --yes TEMP:15          # non-interactive deletion
   .\ti84-evo.ps1 install-context-menu         # current-user Explorer actions

With no path, ``send`` uses ``.ti84-evo-project`` in the current directory. A
file or directory argument sends applicable Python files directly, while
``--archive`` or ``--ram`` overrides the calculator destination.
``pull`` reconstructs source files and ``.ti84-evo-project`` and refuses to
replace changed local files unless ``--force`` is supplied. ``archive`` accepts
one or more variable names; add ``:TYPE`` when a name is ambiguous. ``delete``
uses the same selectors, prints the exact plan, and requires confirmation unless
``--yes`` is supplied. Selecting built-in list ``L1`` through ``L6`` clears its
contents and retains its calculator list slot; custom lists are deleted normally.

The colored terminal UI shows an upload plan, connection state, aligned
progress bars, storage targets, and a final summary. ``list-files`` performs a
read-only directory query and displays each calculator variable's numeric type,
size, and RAM or Archive location. The context-menu installer
adds **Send to TI-84 Evo** for ``.py`` files and folders without requiring
administrator access. Run ``uninstall-context-menu`` to remove those entries.
The CLI is packaged separately but remains in this repository so it shares the
same manifest, incremental state, transport, and protocol implementation as the
PyCharm plugin.

TI Python editor support
------------------------

The plugin exposes typed editor stubs for ``ti_draw``, ``ti_image``,
``ti_system``, ``ti_plotlib``, ``ti_hub``, and ``ti_rover``. They provide
import and member completion, parameter hints, quick documentation, and
type-aware inspections. The stubs are an editor-only synthetic library: they
are not installed as a desktop runtime and are never uploaded to the calculator.

Hardware acceptance
-------------------

The single-file path was accepted on a physical TI-84 Evo on August 21, 2026:
a 29-byte Python source was uploaded as ``EVOTEST`` to RAM, the plugin reported
the completed transfer, and a subsequent directory read returned the new
type-15 program. Multi-file project push still requires separate physical
acceptance, as do Archive-save and project-pull operations.
