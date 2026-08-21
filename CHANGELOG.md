# Changelog

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
- Add **Configure Project…** and one-click **Push Project** tool-window actions.
- Reuse one serial connection while sending one complete Evo variable transaction per declared Python file.
- Include unsaved manifest and source editor changes in project pushes.
- Report aggregate transfer totals and partial progress when a later file fails.
- Avoid PyCharm 2026.2's extension-dropping VFS lookup when configuring multiple project files.
- Refresh a newly created project manifest within the IDE write action required by PyCharm 2026.2.
- Add native host-to-Evo Python program upload.
- Package active `.py` source as a type-15 Evo Python AppVar inside the expected CBOR transfer envelope.
- Add a Kermit sender with negotiated block checks, long packets, quoting, repeat encoding, and element-aligned D-packet chunking.
- Add **Upload Current .py** to the TI-84 Evo tool window.
- Read the current editor document so unsaved editor changes are included in an upload.
- Default calculator program names from the active filename and validate the Evo 1–8 character limit.
- Upload to RAM with overwrite policy enabled for the initial hardware test.

## 0.1.0-SNAPSHOT

- Initial native Kotlin plugin scaffold.
- Evo detection, attributes, and screenshot capture.
