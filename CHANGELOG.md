# Changelog

## 0.4.2

- Add repeatable physical-calculator acceptance with complete preflight backup, packet traces, contained fixtures, post-run cleanup, and recorded firmware and host configuration.
- Detect Python programs edited in place on the calculator before an incremental project push decides that a synchronized local file can be skipped.
- Retry long-running calculator reads across fresh serial sessions, including complete native-variable backups and remote-source verification.
- Upload editable numbers through a native scratch-list conversion, verify firmware 7.0's committed writes after misleading terminal errors, and retry transient scratch cleanup safely.
- Correct IM8C headers and transfer converted type-8 Python images as checksummed native files, falling back to Archive when firmware 7.0 rejects RAM.
- Report the actual image location after a firmware-forced Archive fallback and write restorable, checksummed native files in calculator backups.
- Accept native matrix display names and the Evo's 320×240 framebuffer dimensions during round-trip verification.

## 0.4.1

- Validate the installed plugin against JetBrains Marketplace at startup and on a configurable schedule, with retry controls and consistent unverified, outdated, or developmental version labels.
- Add sanitized Markdown debug information to the About window and remove its redundant dismissal action.
- Add Marauders Lock (`mm.lock`), a hidden developer switch for Mischief Mode with developer polling intervals, verbose Marketplace diagnostics, issue and installed-directory actions, and an explicit deactivation prompt on exit.

## 0.4.0

- Keep PyCharm responsive while pulled project files and their manifest are written to disk.
- Clear built-in lists without deleting them first, preserving the calculator's default L1-L6 List Editor columns.
- Confirm Archive moves from the calculator directory when the final transfer acknowledgement is lost.

## 0.3.1

- Compare the installed plugin with the latest JetBrains Marketplace release, label newer builds as developmental, and flag version or SHA-256 mismatches in the footer and About window.
- Download every type-15 Python program from the calculator, reconstruct local `.py` files and the project manifest, preserve RAM/Archive targets, and protect conflicting local files from silent overwrites.
- Save selected RAM variables to calculator Archive from the PyCharm file browser and the new CLI `archive` command.
- Delete named RAM or Archive variables from the CLI with type disambiguation, an explicit deletion plan, confirmation, progress, and directory-verified partial-failure reporting; adding or clearing built-in lists restores the default L1–L6 List Editor columns through the calculator's scancode endpoint.
- Save full-resolution calculator screenshots as PNG through two Screen-pane buttons or matching right-click actions, including collision-safe one-click saves to the project directory.
- Refresh the Calculator Files directory whenever its tab is opened or clicked and after successful variable, image, Python, project, or Archive uploads.
- Check configured Python names and RAM/Archive targets against the live calculator directory before declaring a project current, restoring unchanged files that were deleted or moved on the calculator.
- Decode native Python AppVars whose declared meaningful length excludes up to three trailing alignment bytes, fixing project pulls for calculator-padded programs.
- Add matching CLI `pull` support with manifest-path reuse, explicit overwrite protection, and synchronized incremental-upload state.
- Unify resource reads and uploads on the Kermit packet codec while preserving the calculator-tested resource framing and extended AUX-byte behavior; add modulo-64 receive sequences, upload ACK validation, and corrected NV/VE transfer errors.
- Add Read the Docs, GitHub Actions, Sourcery, and JetBrains Marketplace badges to the README.

## 0.3.0

- Add a read-only CLI calculator directory command showing native type IDs,
  byte sizes, and RAM or Archive locations.
- Preserve existing List Editor columns when replacing lists by converting the
  edited text through a temporary native list and applying a native replacement.
- Add GitHub, Read the Docs, and bundled-license actions to the About window.
- Convert PNG, JPEG, GIF, and BMP files to compressed Evo Python image variables and upload native `.8ci2`, `.8ca2`, and `.8xv2` files unchanged.
- Add user-level image optimization settings for maximum dimensions and palette size, persisted outside individual projects.
- Add lossless variable inspection/export plus native value decoding and pre-populated creation/replacement editors for calculator numbers, lists, and matrices.
- Build and attach plugin/CLI distributions on version-changing main-branch pushes, publish the plugin to JetBrains Marketplace, and reuse this section for the GitHub release description and plugin What's New notes.

## 0.2.9

- Replace chooser-only project setup with a full configuration window for file order, calculator names, per-entry RAM/Archive targets, and an always-rebuild option.
- Make normal project pushes incremental with successful-file fingerprints and visible multi-file progress.
- Offer a one-time **Push Anyway** action when every configured project file is already up to date.
- Show a clear troubleshooting pop-up when an action cannot reach the calculator.
- Add a separately packaged, color-aware PowerShell CLI and optional current-user Explorer context menus that share the plugin's manifests and upload engine.
- Add a linked table of contents to the glossary.
- Ignore generated local IntelliJ Platform state in Git.

## 0.2.8-SNAPSHOT

- Replace the preliminary bulk RAM-clear action with confirmation-protected deletion for selected rows in the calculator file table, including RAM and Archive variables and partial-failure reporting.

## 0.2.7-SNAPSHOT

- Add a preliminary confirmation-protected **Clear calculator RAM** action that preserves Archive variables and reports partial deletion failures.
- Assert the CDC serial DTR and RTS control lines to match the calculator's working USB handshake.

## 0.2.6-SNAPSHOT

- Normalize system resources into the calculator's nested `hh01` namespace.
- Preserve calculator `E`-frame payloads and include the failed resource URI in diagnostics.
- Present calculator attributes in a grouped details dialog with friendly labels, formatted memory and state values, and a Markdown copy action.
- Replace the marketplace logo with compact light and dark SVG artwork adapted from the TI-84 Evo calculator-and-cable design.
- Lock the pre-publication Marketplace plugin ID to `com.inspyresoftworks.ti84evo` and rely on the IntelliJ Platform's bundled Kotlin standard library.
- Audit the README and Sphinx documentation against the implementation, build configuration, CI workflow, and hardware-acceptance status.
- Add a glossary for USB, protocol, transfer, display, memory, and IDE terminology.

## 0.2.5-SNAPSHOT

- Show the installed plugin version in the tool-window footer and About dialog.
- Add the root `VERSION` file as the canonical version source for builds, plugin metadata, and documentation.

## 0.2.4-SNAPSHOT

- Add a sortable calculator file browser showing decoded variable names, types, byte sizes, and RAM/Archive locations.
- Decode Evo custom names, standard token names, list names, and function names from the directory CBOR resource.
- Decode Kermit control quoting and repeat encoding for dynamic resources whose length is announced as zero.
- Verify the file browser and single-file Python upload against a physical TI-84 Evo.
- Move marketplace icons to the supported `META-INF/pluginIcon*.svg` locations and remove invalid descriptor elements.
- Replace the clipped Swing button row with a native JetBrains icon toolbar that keeps a visible overflow menu at narrow widths.
- Move connection and operation status onto its own persistent, icon-coded line below the toolbar.
- Add typed PyCharm import resolution, completion, parameter hints, and quick documentation for the complete TI module family: `ti_draw`, `ti_image`, `ti_system`, `ti_plotlib`, `ti_hub`, and `ti_rover`.
- Offer all six TI modules while typing `from ti_` or `import ti_`, before an import has fully resolved.
- Complete public firmware symbols while typing `from ti_draw import …` and the equivalent form for every bundled TI module.
- Let PyCharm's resolver provide `from … import …` symbols once, while retaining the automatic popup trigger.
- Add `.ti84-evo-project` manifests for declaring multiple project source files and their calculator program names.
- Add **Configure project** and one-click **Push project** tool-window actions.
- Reuse one serial connection while sending one complete Evo variable transaction per declared Python file.
- Include unsaved manifest and source editor changes in project pushes.
- Report aggregate transfer totals and partial progress when a later file fails.
- Avoid PyCharm 2026.2's extension-dropping VFS lookup when configuring multiple project files.
- Refresh a newly created project manifest within the IDE write action required by PyCharm 2026.2.
- Add native host-to-Evo Python program upload.
- Package active `.py` source as a type-15 Evo Python AppVar inside the expected CBOR transfer envelope.
- Add a Kermit sender with negotiated block checks, long packets, quoting, repeat encoding, and element-aligned D-packet chunking.
- Add **Upload current Python file** to the TI-84 Evo tool window.
- Read the current editor document so unsaved editor changes are included in an upload.
- Default calculator program names from the active filename and validate the Evo 1–8 character limit.
- Upload to RAM with overwrite policy enabled for the initial hardware test.

## 0.1.0-SNAPSHOT

- Initial native Kotlin plugin scaffold.
- Evo detection, attributes, and screenshot capture.
