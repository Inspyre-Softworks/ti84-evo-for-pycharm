package com.inspyresoftworks.ti84evo.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.protocol.EvoPythonPayload
import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectPuller
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.protocol.EvoVariableArchiveException
import com.inspyresoftworks.ti84evo.protocol.EvoVariableDeleteException
import com.inspyresoftworks.ti84evo.protocol.EvoImagePayload
import com.inspyresoftworks.ti84evo.protocol.EvoVariablePayload
import com.inspyresoftworks.ti84evo.protocol.isPersistentBuiltInList
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import com.inspyresoftworks.ti84evo.project.EvoProjectPull
import com.inspyresoftworks.ti84evo.project.EvoProjectUploadState
import com.inspyresoftworks.ti84evo.service.EvoDeviceService
import com.inspyresoftworks.ti84evo.service.EvoMarketplaceService
import com.inspyresoftworks.ti84evo.service.EvoMischiefMode
import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.Image
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.table.DefaultTableModel
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * TI-84 Evo tool-window UI.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private data class DirectoryRefreshRequest(
        val showSummary: Boolean,
        val completedOperation: String? = null,
    )

    private companion object {
        const val PLUGIN_ID = "com.inspyresoftworks.ti84evo"
        val SCREENSHOT_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }

    private val service = project.getService(EvoDeviceService::class.java)
    private val applicationSettings = ApplicationManager.getApplication().getService(EvoApplicationSettings::class.java)
    private val marketplaceService = ApplicationManager.getApplication().getService(EvoMarketplaceService::class.java)
    private val mischiefMode: Boolean
        get() = EvoMischiefMode.isActive()
    private val installedPluginVersion = EvoBuildInfo.version
    private var versionStatus = EvoVersionStatus.checking(installedPluginVersion)
    private var aboutDialog: EvoAboutDialog? = null
    private val status = JBLabel("Not checked", AllIcons.General.Information, JBLabel.LEADING)
    private val version = JBLabel(
        versionStatus.footerText,
        JBLabel.TRAILING,
    ).apply {
        toolTipText = versionStatus.tooltip
        foreground = com.intellij.ui.JBColor.GRAY
    }
    private val uploadProgress = JProgressBar().apply {
        isVisible = false
        isStringPainted = true
    }
    private val output = JBTextArea().apply {
        isEditable = false
        lineWrap = false
    }
    private val screen = JBLabel("No screenshot", JBLabel.CENTER).apply {
        preferredSize = Dimension(640, 480)
    }
    private var capturedScreenImage: BufferedImage? = null
    private val saveScreenAsButton = JButton("Save As…", AllIcons.Actions.MenuSaveall).apply {
        isEnabled = false
        toolTipText = "Save the full-resolution calculator screenshot as a PNG"
        addActionListener { saveScreenshotAs() }
    }
    private val saveScreenToProjectButton = JButton("Save to Project Dir", AllIcons.Nodes.Folder).apply {
        isEnabled = false
        toolTipText = "Save the full-resolution calculator screenshot in the project directory"
        addActionListener { saveScreenshotToProject() }
    }
    private val saveScreenAsMenuItem = JMenuItem("Save As…", AllIcons.Actions.MenuSaveall).apply {
        isEnabled = false
        addActionListener { saveScreenshotAs() }
    }
    private val saveScreenToProjectMenuItem = JMenuItem("Save to Project Dir", AllIcons.Nodes.Folder).apply {
        isEnabled = false
        addActionListener { saveScreenshotToProject() }
    }
    private val screenPopupMenu = JPopupMenu().apply {
        add(saveScreenAsMenuItem)
        add(saveScreenToProjectMenuItem)
    }
    private val directoryModel = object : DefaultTableModel(
        arrayOf("Name", "Type", "Size", "Location"),
        0,
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false

        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            2 -> Long::class.javaObjectType
            else -> String::class.java
        }
    }
    private val directoryTable = JBTable(directoryModel).apply {
        autoCreateRowSorter = true
        fillsViewportHeight = true
        emptyText.text = "Press Browse calculator files to read the directory"
        columnModel.getColumn(0).preferredWidth = 180
        columnModel.getColumn(1).preferredWidth = 140
        columnModel.getColumn(2).preferredWidth = 90
        columnModel.getColumn(3).preferredWidth = 80
    }
    private val directoryEntries = mutableListOf<EvoDirectoryEntry>()
    private var directoryRefreshInProgress = false
    private var queuedDirectoryRefresh: DirectoryRefreshRequest? = null
    private var deletionInProgress = false
    private val deleteSelectedButton = JButton("Delete selected", AllIcons.General.Remove).apply {
        isEnabled = false
        toolTipText = "Delete the selected calculator files after confirmation"
        addActionListener { deleteSelectedFiles() }
    }
    private val archiveSelectedButton = JButton("Save to Archive", AllIcons.Actions.Download).apply {
        isEnabled = false
        toolTipText = "Move the selected RAM variables into calculator Archive"
        addActionListener { archiveSelectedFiles() }
    }
    private val viewSelectedButton = JButton("View / edit", AllIcons.Actions.Show).apply {
        isEnabled = false
        toolTipText = "Inspect or export the selected variable; replace supported numeric data"
        addActionListener { viewSelectedVariable() }
    }
    private val addVariableButton = JButton("Add variable", AllIcons.General.Add).apply {
        toolTipText = "Create a calculator number, list, or matrix"
        addActionListener { addVariable() }
    }
    private val screenPane = JBScrollPane(screen)
    private val screenContent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(screenPane, BorderLayout.CENTER)
        add(
            JPanel(GridLayout(1, 2, 8, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(4)
                add(saveScreenAsButton)
                add(saveScreenToProjectButton)
            },
            BorderLayout.SOUTH,
        )
    }
    private val directoryPane = JBScrollPane(directoryTable)
    private val directoryContent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(directoryPane, BorderLayout.CENTER)
        add(
            JPanel(GridLayout(2, 2, 8, 4)).apply {
                isOpaque = false
                add(viewSelectedButton)
                add(addVariableButton)
                add(archiveSelectedButton)
                add(deleteSelectedButton)
            },
            BorderLayout.SOUTH,
        )
    }
    private val contentTabs = JBTabbedPane().apply {
        addTab("Screen", screenContent)
        addTab("Calculator Files", directoryContent)
    }

    init {
        border = JBUI.Borders.empty(8)
        screen.componentPopupMenu = screenPopupMenu
        directoryTable.selectionModel.addListSelectionListener {
            updateDeleteSelectedButtonState()
        }
        contentTabs.addChangeListener {
            if (contentTabs.selectedComponent === directoryContent) browseFiles()
        }
        contentTabs.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                val tabIndex = contentTabs.indexAtLocation(event.x, event.y)
                if (
                    tabIndex >= 0 &&
                    contentTabs.getComponentAt(tabIndex) === directoryContent &&
                    contentTabs.selectedComponent === directoryContent
                ) {
                    browseFiles()
                }
            }
        })

        val toolbar = EvoToolWindowToolbar.create(
            this,
            EvoToolWindowActions(
                refresh = ::refresh,
                readAttributes = ::readAttributes,
                browseFiles = ::browseFiles,
                captureScreen = ::captureScreen,
                uploadCurrentPython = ::uploadCurrentPython,
                uploadPicture = ::uploadPicture,
                configureProject = ::configureProject,
                configureTransfers = ::configureTransfers,
                pushProject = ::pushProject,
                pullProject = ::pullProject,
                showAbout = ::showAbout,
            ),
        )
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(8)
            add(toolbar.component, BorderLayout.NORTH)
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(status.apply { border = JBUI.Borders.empty(5, 4, 3, 4) }, BorderLayout.NORTH)
                    add(uploadProgress, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
        }

        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            contentTabs,
            JBScrollPane(output),
        ).apply {
            resizeWeight = 0.72
        }

        add(header, BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(3, 4, 0, 4)
                add(version, BorderLayout.EAST)
            },
            BorderLayout.SOUTH,
        )
        marketplaceService.addListener(this, ::updateVersionStatus)
        refresh()
    }

    private fun updateVersionStatus(checked: EvoVersionStatus) {
        onEdt {
            if (!project.isDisposed) {
                versionStatus = checked
                version.text = checked.footerText
                version.toolTipText = checked.tooltip
                aboutDialog?.updateVersionStatus(checked)
            }
        }
    }

    private fun refresh() {
        showStatus("Scanning for calculators…", StatusKind.WORKING)
        service.detect { result ->
            onEdt {
                result.onSuccess { ports ->
                    val text = when (ports.size) {
                        0 -> "No TI-84 Evo connected"
                        1 -> "Connected: ${ports.single()}"
                        else -> "${ports.size} TI-84 Evo devices found"
                    }
                    showStatus(text, if (ports.isEmpty()) StatusKind.IDLE else StatusKind.CONNECTED)
                    output.text = ports.joinToString(separator = "\n", prefix = "Detected ports:\n")
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun readAttributes() {
        showStatus("Reading calculator attributes…", StatusKind.WORKING)
        service.readAttributes { result ->
            onEdt {
                result.onSuccess { attributes ->
                    showStatus("Connected — attributes received", StatusKind.CONNECTED)
                    output.text = EvoAttributePresentation.toMarkdown(attributes)
                    EvoAttributesDialog(project, attributes).show()
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun browseFiles() {
        if (contentTabs.selectedComponent !== directoryContent) {
            contentTabs.selectedComponent = directoryContent
            return
        }
        requestDirectoryRefresh(DirectoryRefreshRequest(showSummary = true))
    }

    private fun refreshDirectoryAfterOperation(completedOperation: String) {
        requestDirectoryRefresh(
            DirectoryRefreshRequest(
                showSummary = false,
                completedOperation = completedOperation,
            ),
        )
    }

    private fun requestDirectoryRefresh(request: DirectoryRefreshRequest) {
        if (directoryRefreshInProgress) {
            val queued = queuedDirectoryRefresh
            queuedDirectoryRefresh = when {
                request.showSummary -> request
                queued?.showSummary == true -> queued
                else -> request
            }
            return
        }

        directoryRefreshInProgress = true
        if (request.showSummary) {
            directoryTable.emptyText.text = "Reading calculator directory…"
            showStatus("Reading calculator directory…", StatusKind.WORKING)
        }
        service.readDirectory { result ->
            onEdt {
                try {
                    result.onSuccess { entries ->
                        replaceDirectoryEntries(entries)
                        if (request.showSummary) {
                            showDirectorySummary(entries)
                        } else {
                            val operation = request.completedOperation ?: "Operation complete"
                            showStatus("$operation — directory refreshed", StatusKind.CONNECTED)
                            output.append("\nDirectory refreshed: ${entries.size} calculator file(s).")
                        }
                    }.onFailure { error ->
                        if (request.showSummary) {
                            showFailure(error)
                        } else {
                            val operation = request.completedOperation ?: "Operation complete"
                            showStatus("$operation — directory refresh failed", StatusKind.ERROR)
                            output.append(
                                "\nDirectory refresh failed: ${error.message ?: error.javaClass.simpleName}",
                            )
                        }
                    }
                } finally {
                    directoryRefreshInProgress = false
                    val queued = queuedDirectoryRefresh
                    queuedDirectoryRefresh = null
                    if (queued != null) requestDirectoryRefresh(queued)
                }
            }
        }
    }

    private fun replaceDirectoryEntries(entries: List<EvoDirectoryEntry>) {
        val selectedIdentities = selectedDirectoryEntries().mapTo(mutableSetOf(), ::entryIdentity)
        directoryModel.rowCount = 0
        directoryEntries.clear()
        directoryEntries += entries.sortedBy { it.name.lowercase() }
        directoryEntries.forEach { entry ->
            directoryModel.addRow(
                arrayOf<Any>(
                    entry.name,
                    "${entry.typeName} (${entry.type})",
                    entry.size,
                    entry.location,
                ),
            )
        }
        directoryTable.clearSelection()
        directoryEntries.forEachIndexed { modelRow, entry ->
            if (entryIdentity(entry) in selectedIdentities) {
                val viewRow = directoryTable.convertRowIndexToView(modelRow)
                if (viewRow >= 0) directoryTable.addRowSelectionInterval(viewRow, viewRow)
            }
        }
        directoryTable.emptyText.text = if (entries.isEmpty()) {
            "No calculator files were returned"
        } else {
            "Press Calculator Files to refresh the directory"
        }
        updateDeleteSelectedButtonState()
    }

    private fun showDirectorySummary(entries: List<EvoDirectoryEntry>) {
        showStatus("Connected — ${entries.size} calculator files", StatusKind.CONNECTED)
        val ramEntries = entries.filterNot { it.archived }
        val archiveEntries = entries.filter { it.archived }
        output.text = buildString {
            appendLine("Calculator directory")
            appendLine("Variables: ${entries.size}")
            appendLine("RAM: ${ramEntries.size} variables, ${ramEntries.sumOf { it.size }} bytes")
            appendLine("Archive: ${archiveEntries.size} variables, ${archiveEntries.sumOf { it.size }} bytes")
            if (entries.isEmpty()) append("No variables were returned by the directory resource.")
        }.trimEnd()
    }

    private fun captureScreen() {
        showStatus("Capturing calculator screen…", StatusKind.WORKING)
        service.captureScreen { result ->
            onEdt {
                result.onSuccess { capture ->
                    showStatus(
                        "Connected — ${capture.width}×${capture.height}×${capture.bitsPerPixel}",
                        StatusKind.CONNECTED,
                    )
                    val image = EvoImage.toBufferedImage(capture)
                    capturedScreenImage = image
                    updateScreenshotSaveActions()
                    val scale = minOf(2.0, 640.0 / image.width, 480.0 / image.height)
                    val scaled = image.getScaledInstance(
                        (image.width * scale).toInt(),
                        (image.height * scale).toInt(),
                        Image.SCALE_FAST,
                    )
                    screen.text = null
                    screen.icon = ImageIcon(scaled)
                    contentTabs.selectedComponent = screenContent
                    output.text = capture.metadata.entries.joinToString("\n") { (key, value) -> "$key: ${formatValue(value)}" }
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun updateScreenshotSaveActions() {
        val enabled = capturedScreenImage != null
        saveScreenAsButton.isEnabled = enabled
        saveScreenToProjectButton.isEnabled = enabled
        saveScreenAsMenuItem.isEnabled = enabled
        saveScreenToProjectMenuItem.isEnabled = enabled
    }

    private fun saveScreenshotAs() {
        if (capturedScreenImage == null) return
        val initialDirectory = project.basePath?.let(::File)
        val chooser = JFileChooser(initialDirectory).apply {
            dialogTitle = "Save TI-84 Evo Screenshot"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("PNG image (*.png)", "png")
            selectedFile = (initialDirectory?.toPath() ?: Path.of(System.getProperty("user.home")))
                .resolve(screenshotFileName())
                .toFile()
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile.toPath().toAbsolutePath().normalize()
        val path = if (selected.fileName.toString().endsWith(".png", ignoreCase = true)) {
            selected
        } else {
            selected.resolveSibling("${selected.fileName}.png")
        }
        if (Files.exists(path)) {
            val overwrite = Messages.showYesNoDialog(
                project,
                "Replace the existing file?\n\n$path",
                "Save TI-84 Evo Screenshot",
                Messages.getWarningIcon(),
            )
            if (overwrite != Messages.YES) return
        }
        writeScreenshot(path)
    }

    private fun saveScreenshotToProject() {
        val root = projectRoot() ?: return
        val fileName = screenshotFileName()
        val stem = fileName.removeSuffix(".png")
        var path = root.resolve(fileName)
        var suffix = 2
        while (Files.exists(path)) {
            path = root.resolve("$stem-$suffix.png")
            suffix++
        }
        writeScreenshot(path)
    }

    private fun writeScreenshot(path: Path) {
        val image = capturedScreenImage ?: return
        runCatching {
            path.parent?.let(Files::createDirectories)
            if (!ImageIO.write(image, "png", path.toFile())) {
                throw IllegalStateException("No PNG image writer is available")
            }
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        }.onSuccess {
            showStatus("Saved screenshot: ${path.fileName}", StatusKind.READY)
            output.append("\nScreenshot saved to $path")
        }.onFailure(::showFailure)
    }

    private fun screenshotFileName(): String =
        "ti84-evo-screen-${LocalDateTime.now().format(SCREENSHOT_TIMESTAMP)}.png"

    private fun deleteSelectedFiles() {
        if (deletionInProgress) return
        val selectedEntries = selectedDirectoryEntries()
        if (selectedEntries.isEmpty()) return

        val answer = Messages.showYesNoDialog(
            project,
            buildString {
                appendLine(
                    if (selectedEntries.size == 1) {
                        val entry = selectedEntries.single()
                        if (isPersistentBuiltInList(entry)) {
                            "Clear ${entry.name} and restore the default L1-L6 List Editor columns?"
                        } else {
                            "Delete ${entry.name} from the calculator?"
                        }
                    } else {
                        "Remove or clear ${selectedEntries.size} selected calculator files?"
                    },
                )
                appendLine()
                selectedEntries.take(10).forEach { entry ->
                    val action = if (isPersistentBuiltInList(entry)) "clear values" else "delete"
                    appendLine("• ${entry.name}: $action (${entry.typeName}, ${entry.location})")
                }
                if (selectedEntries.size > 10) {
                    appendLine("• …and ${selectedEntries.size - 10} more")
                }
                appendLine()
                append("This cannot be undone.")
            },
            "Delete Calculator Files",
            Messages.getWarningIcon(),
        )
        if (answer != Messages.YES) return

        deletionInProgress = true
        updateDeleteSelectedButtonState()
        showStatus("Deleting ${selectedEntries.size} calculator file(s)…", StatusKind.WORKING)
        uploadProgress.minimum = 0
        uploadProgress.maximum = selectedEntries.size
        uploadProgress.value = 0
        uploadProgress.string = "Preparing deletion…"
        uploadProgress.isVisible = true
        output.text = "Deleting ${selectedEntries.joinToString { it.name }}…"
        service.deleteVariables(
            selectedEntries,
            onProgress = { entry, completed, total ->
                onEdt {
                    uploadProgress.value = completed
                    val action = if (isPersistentBuiltInList(entry)) "Cleared" else "Deleted"
                    uploadProgress.string = "$action $completed/$total: ${entry.name}"
                }
            },
        ) { result ->
            onEdt {
                try {
                    result.onSuccess { completedEntries ->
                        val deletedEntries = completedEntries.filterNot(::isPersistentBuiltInList)
                        val clearedEntries = completedEntries.filter(::isPersistentBuiltInList)
                        removeDirectoryEntries(deletedEntries)
                        showStatus("Completed ${completedEntries.size} calculator file operation(s)", StatusKind.CONNECTED)
                        output.text = buildString {
                            appendLine("Calculator file operation successful")
                            completedEntries.forEach {
                                val action = if (isPersistentBuiltInList(it)) "Cleared" else "Deleted"
                                appendLine("• $action ${it.name} (${it.typeName}, ${it.size} bytes, ${it.location})")
                            }
                            if (clearedEntries.isNotEmpty()) {
                                appendLine("List Editor: restored default L1-L6 columns")
                            }
                        }
                        val summary = buildString {
                            if (deletedEntries.isNotEmpty()) append("Deleted ${deletedEntries.size}")
                            if (deletedEntries.isNotEmpty() && clearedEntries.isNotEmpty()) append(", ")
                            if (clearedEntries.isNotEmpty()) append("cleared ${clearedEntries.size}")
                        }
                        refreshDirectoryAfterOperation(summary)
                    }.onFailure { error ->
                        if (error is EvoVariableDeleteException) {
                            removeDirectoryEntries(error.deletedEntries)
                        }
                        showFailure(error)
                    }
                } finally {
                    uploadProgress.isVisible = false
                    deletionInProgress = false
                    updateDeleteSelectedButtonState()
                }
            }
        }
    }

    private fun archiveSelectedFiles() {
        if (deletionInProgress) return
        val selectedEntries = selectedDirectoryEntries().filterNot { it.archived }
        if (selectedEntries.isEmpty()) return

        val answer = Messages.showYesNoDialog(
            project,
            buildString {
                appendLine(
                    if (selectedEntries.size == 1) {
                        "Save ${selectedEntries.single().name} to calculator Archive?"
                    } else {
                        "Save ${selectedEntries.size} selected files to calculator Archive?"
                    },
                )
                appendLine()
                selectedEntries.take(10).forEach { entry -> appendLine("• ${entry.name} (${entry.typeName})") }
                if (selectedEntries.size > 10) appendLine("• …and ${selectedEntries.size - 10} more")
            },
            "Save Calculator Files to Archive",
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return

        deletionInProgress = true
        updateDeleteSelectedButtonState()
        showStatus("Archiving ${selectedEntries.size} calculator file(s)…", StatusKind.WORKING)
        uploadProgress.minimum = 0
        uploadProgress.maximum = selectedEntries.size
        uploadProgress.value = 0
        uploadProgress.string = "Preparing Archive transfer…"
        uploadProgress.isVisible = true
        service.archiveVariables(
            selectedEntries,
            onProgress = { archive, completed, total ->
                onEdt {
                    uploadProgress.value = completed
                    uploadProgress.string = "Archived $completed/$total: ${archive.entry.name}"
                }
            },
        ) { result ->
            onEdt {
                try {
                    result.onSuccess { archived ->
                        markDirectoryEntriesArchived(archived.map { it.entry })
                        showStatus("Saved ${archived.size} file(s) to Archive", StatusKind.CONNECTED)
                        output.text = buildString {
                            appendLine("Archive transfer successful")
                            archived.forEach { result ->
                                appendLine("• ${result.entry.name}: ${result.payloadBytes} bytes, ${result.packets} packets")
                            }
                        }
                        refreshDirectoryAfterOperation("Saved ${archived.size} file(s) to Archive")
                    }.onFailure { error ->
                        if (error is EvoVariableArchiveException) {
                            markDirectoryEntriesArchived(error.archivedEntries)
                        }
                        showFailure(error)
                    }
                } finally {
                    uploadProgress.isVisible = false
                    deletionInProgress = false
                    updateDeleteSelectedButtonState()
                }
            }
        }
    }

    private fun selectedDirectoryEntries(): List<EvoDirectoryEntry> = directoryTable.selectedRows
        .map(directoryTable::convertRowIndexToModel)
        .distinct()
        .sorted()
        .mapNotNull(directoryEntries::getOrNull)

    private fun markDirectoryEntriesArchived(entries: List<EvoDirectoryEntry>) {
        val archivedByIdentity = entries.associateBy(::entryIdentity)
        directoryEntries.indices.forEach { modelRow ->
            archivedByIdentity[entryIdentity(directoryEntries[modelRow])]?.let { archived ->
                directoryEntries[modelRow] = archived
                directoryModel.setValueAt(archived.location, modelRow, 3)
            }
        }
    }

    private fun removeDirectoryEntries(entries: List<EvoDirectoryEntry>) {
        val remainingByIdentity = mutableMapOf<String, Int>()
        entries.forEach { entry ->
            val identity = entryIdentity(entry)
            remainingByIdentity[identity] = (remainingByIdentity[identity] ?: 0) + 1
        }

        for (modelRow in directoryEntries.lastIndex downTo 0) {
            val identity = entryIdentity(directoryEntries[modelRow])
            val remaining = remainingByIdentity[identity] ?: 0
            if (remaining > 0) {
                directoryEntries.removeAt(modelRow)
                directoryModel.removeRow(modelRow)
                if (remaining == 1) {
                    remainingByIdentity.remove(identity)
                } else {
                    remainingByIdentity[identity] = remaining - 1
                }
            }
        }
        updateDeleteSelectedButtonState()
    }

    private fun entryIdentity(entry: EvoDirectoryEntry): String =
        "${entry.type}:${entry.tokenName.joinToString(separator = ",") { byte -> "%02X".format(byte.toInt() and 0xFF) }}"

    private fun updateDeleteSelectedButtonState() {
        deleteSelectedButton.isEnabled =
            !deletionInProgress &&
                directoryEntries.isNotEmpty() &&
                directoryTable.selectedRowCount > 0
        viewSelectedButton.isEnabled = !deletionInProgress && directoryTable.selectedRowCount == 1
        archiveSelectedButton.isEnabled = !deletionInProgress && selectedDirectoryEntries().any { !it.archived }
    }

    private fun viewSelectedVariable() {
        val row = directoryTable.selectedRow.takeIf { it >= 0 } ?: return
        val entry = directoryEntries[directoryTable.convertRowIndexToModel(row)]
        showStatus("Reading ${entry.name}…", StatusKind.WORKING)
        service.readVariable(entry) { result ->
            onEdt {
                result.onSuccess { raw ->
                    showStatus("Read ${entry.name}", StatusKind.CONNECTED)
                    val dialog = EvoVariableContentsDialog(project, entry, raw)
                    dialog.show()
                    if (dialog.shouldReplace) dialog.editableValue?.let(::replaceVariable)
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun addVariable() {
        val dialog = EvoVariableEditorDialog(project)
        if (!dialog.showAndGet()) return
        sendEditableVariable(dialog.editedValue)
    }

    private fun replaceVariable(initial: EvoVariablePayload.EditableValue) {
        val editableName = when (initial.kind) {
            EvoVariablePayload.Kind.MATRIX -> initial.name.removeSurrounding("[", "]")
            else -> initial.name
        }
        val dialog = EvoVariableEditorDialog(
            project = project,
            initialKind = initial.kind,
            initialName = editableName,
            initialArchived = initial.archived,
            initialValue = initial.value,
            replacing = true,
        )
        if (!dialog.showAndGet()) return
        sendEditableVariable(dialog.editedValue)
    }

    private fun sendEditableVariable(value: EvoVariablePayload.EditableValue) {
        showStatus("Sending ${value.kind.wireName.lowercase()} ${value.name}…", StatusKind.WORKING)
        output.text = "Preparing ${value.kind.wireName.lowercase()} ${value.name} for the calculator…"
        service.uploadEditableVariable(value) { result ->
            onEdt {
                result.onSuccess { upload ->
                    showStatus("Sent ${upload.description}", StatusKind.CONNECTED)
                    output.text = buildString {
                        appendLine("Variable upload successful")
                        appendLine("Variable: ${upload.description}")
                        appendLine("Payload: ${upload.payloadBytes} bytes")
                        appendLine("Kermit packets: ${upload.packets}")
                        if (upload.restoredBuiltInListEditor) {
                            appendLine("List Editor: restored default L1-L6 columns")
                        } else if (upload.preservedListEditor) {
                            appendLine("List Editor: existing column registration preserved")
                        }
                        append("Target: ${if (upload.archived) "Archive" else "RAM"}")
                    }
                    refreshDirectoryAfterOperation("Sent ${upload.description}")
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun uploadPicture() {
        val chooser = JFileChooser(project.basePath?.let { java.io.File(it) }).apply {
            dialogTitle = "Upload Picture to TI-84 Evo"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            addChoosableFileFilter(FileNameExtensionFilter("Desktop images (*.png, *.jpg, *.gif, *.bmp)", "png", "jpg", "jpeg", "gif", "bmp"))
            addChoosableFileFilter(FileNameExtensionFilter("Evo variable files (*.8ci2, *.8ca2, *.8xv2)", "8ci2", "8ca2", "8xv2"))
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val path = chooser.selectedFile.toPath()
        val extension = chooser.selectedFile.extension.lowercase()
        val archiveChoice = Messages.showDialog(
            project,
            "Where should ${chooser.selectedFile.name} be stored?",
            "Upload Picture to TI-84 Evo",
            arrayOf("Archive", "RAM", "Cancel"),
            0,
            Messages.getQuestionIcon(),
        )
        if (archiveChoice !in 0..1) return
        val archived = archiveChoice == 0

        if (extension in setOf("8ci2", "8ca2", "8xv2")) {
            showStatus("Uploading ${chooser.selectedFile.name}…", StatusKind.WORKING)
            service.uploadVariableFile(path, archived) { result ->
                onEdt {
                    result.onSuccess { upload ->
                        showStatus("Uploaded ${upload.description}", StatusKind.CONNECTED)
                        output.text = "Uploaded ${upload.description}\n${upload.payloadBytes} bytes, ${upload.packets} packets\nTarget: ${if (archived) "Archive" else "RAM"}"
                        refreshDirectoryAfterOperation("Uploaded ${upload.description}")
                    }.onFailure { showFailure(it) }
                }
            }
            return
        }

        val defaultName = EvoImagePayload.defaultName(chooser.selectedFile.nameWithoutExtension)
        val name = Messages.showInputDialog(
            project,
            "Python image variable name (1–8 letters, digits, or underscores):",
            "Upload Picture to TI-84 Evo",
            Messages.getQuestionIcon(),
            defaultName,
            object : InputValidator {
                override fun checkInput(inputString: String): Boolean = EvoImagePayload.isValidName(inputString.uppercase())
                override fun canClose(inputString: String): Boolean = checkInput(inputString)
            },
        ) ?: return

        showStatus("Converting ${chooser.selectedFile.name}…", StatusKind.WORKING)
        output.text = "Converting ${chooser.selectedFile.name} with your image transfer settings…"
        service.uploadImage(path, name.uppercase(), archived, applicationSettings.snapshot()) { result ->
            onEdt {
                result.onSuccess { upload ->
                    showStatus("Uploaded image ${upload.image.name}", StatusKind.CONNECTED)
                    output.text = buildString {
                        appendLine("Picture upload successful")
                        appendLine("Variable: ${upload.image.name}")
                        appendLine("Original: ${upload.image.sourceWidth}×${upload.image.sourceHeight}")
                        appendLine("Converted: ${upload.image.width}×${upload.image.height}, ${upload.image.colors} colors")
                        appendLine("Transfer payload: ${upload.transfer.payloadBytes} bytes")
                        appendLine("Kermit packets: ${upload.transfer.packets}")
                        append("Target: ${if (archived) "Archive" else "RAM"}")
                    }
                    refreshDirectoryAfterOperation("Uploaded image ${upload.image.name}")
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun configureTransfers() {
        EvoTransferSettingsDialog(project, applicationSettings, mischiefMode, marketplaceService::settingsChanged).show()
    }

    private fun uploadCurrentPython() {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (file == null) {
            Messages.showWarningDialog(project, "Open a Python file first.", "TI-84 Evo")
            return
        }
        if (!file.extension.equals("py", ignoreCase = true)) {
            Messages.showWarningDialog(
                project,
                "The active editor is not a .py file: ${file.name}",
                "TI-84 Evo",
            )
            return
        }

        val defaultName = EvoPythonPayload.defaultProgramName(file.nameWithoutExtension)
        val programName = Messages.showInputDialog(
            project,
            "Calculator program name (1–8 letters or digits):",
            "Upload Python to TI-84 Evo",
            Messages.getQuestionIcon(),
            defaultName,
            object : InputValidator {
                override fun checkInput(inputString: String): Boolean =
                    EvoPythonPayload.isValidProgramName(inputString)

                override fun canClose(inputString: String): Boolean = checkInput(inputString)
            },
        ) ?: return

        val source = ApplicationManager.getApplication().runReadAction<String> {
            FileDocumentManager.getInstance().getDocument(file)?.text
                ?: String(file.contentsToByteArray(), StandardCharsets.UTF_8)
        }

        val targetChoice = Messages.showDialog(
            project,
            "Where should $programName be stored?",
            "Upload Python to TI-84 Evo",
            arrayOf("Archive", "RAM", "Cancel"),
            1,
            Messages.getQuestionIcon(),
        )
        if (targetChoice == 2 || targetChoice == -1) return
        val archive = targetChoice == 0

        showStatus("Uploading ${file.name}…", StatusKind.WORKING)
        output.text = "Packaging ${file.name} as ${programName.uppercase()}…"

        service.uploadPython(source, programName, archive) { result ->
            onEdt {
                result.onSuccess { upload ->
                    showStatus("Uploaded ${upload.programName}", StatusKind.CONNECTED)
                    output.text = buildString {
                        appendLine("Upload successful")
                        appendLine("Program: ${upload.programName}")
                        appendLine("Source: ${upload.sourceBytes} bytes")
                        appendLine("Transfer payload: ${upload.payloadBytes} bytes")
                        appendLine("Kermit packets: ${upload.packets}")
                        appendLine("Target: ${if (upload.archived) "Archive" else "RAM"}")
                    }
                    refreshDirectoryAfterOperation("Uploaded ${upload.programName}")
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun configureProject() {
        val root = projectRoot() ?: return
        val manifestPath = root.resolve(EvoProjectManifest.FILE_NAME)
        val existingConfiguration = runCatching {
            if (Files.isRegularFile(manifestPath)) {
                EvoProjectManifest.parseConfiguration(Files.readString(manifestPath, StandardCharsets.UTF_8))
            } else {
                EvoProjectManifest.Configuration(emptyList())
            }
        }.getOrElse {
            showFailure(it)
            return
        }
        val dialog = EvoProjectConfigurationDialog(
            project,
            root,
            existingConfiguration.entries,
            existingConfiguration.alwaysPushAll,
        )
        if (!dialog.showAndGet()) return
        val manifestText = EvoProjectManifest.renderEntries(dialog.entries, dialog.shouldAlwaysPushAll)

        runCatching {
            val manifestFile = ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                Files.writeString(manifestPath, manifestText, StandardCharsets.UTF_8)
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(manifestPath)
                    ?: error("Created manifest could not be opened: $manifestPath")
            }
            FileEditorManager.getInstance(project).openFile(manifestFile, true)
        }.onSuccess {
            showStatus("Project configured: ${dialog.entries.size} files", StatusKind.READY)
            output.text = buildString {
                appendLine("Saved ${EvoProjectManifest.FILE_NAME}")
                appendLine("${dialog.entries.size} Python files are configured.")
                append("Press Push Project to upload files changed since the last successful push.")
            }
        }.onFailure { showFailure(it) }
    }

    private fun pushProject() {
        val root = projectRoot() ?: return
        val resolvedProject = runCatching { readProjectPrograms(root) }.getOrElse { error ->
            if (error is java.nio.file.NoSuchFileException) {
                Messages.showWarningDialog(
                    project,
                    "No ${EvoProjectManifest.FILE_NAME} was found. Press Configure Project… first.",
                    "TI-84 Evo",
                )
            } else {
                showFailure(error)
            }
            return
        }
        val configured = resolvedProject.programs

        showStatus("Checking calculator project state…", StatusKind.WORKING)
        output.text = "Comparing configured Python files with the calculator directory…"
        service.readDirectory { result ->
            onEdt {
                result.onSuccess { calculatorDirectory ->
                    replaceDirectoryEntries(calculatorDirectory)
                    pushResolvedProject(root, resolvedProject, configured, calculatorDirectory)
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun pushResolvedProject(
        root: Path,
        resolvedProject: ResolvedProject,
        configured: List<ResolvedProjectProgram>,
        calculatorDirectory: List<EvoDirectoryEntry>,
    ) {
        var pending = if (resolvedProject.alwaysPushAll) {
            configured
        } else {
            val pendingPairs = EvoProjectUploadState.pending(
                root,
                configured.map { it.entry to it.source },
                calculatorDirectory,
            )
            val pendingPaths = pendingPairs.mapTo(mutableSetOf()) { it.first.sourcePath }
            configured.filter { it.entry.sourcePath in pendingPaths }
        }
        var pushAnyway = false
        if (pending.isEmpty()) {
            showStatus("Project is up to date", StatusKind.READY)
            output.text = "Every configured source is unchanged and present in the requested calculator location."
            val choice = Messages.showDialog(
                project,
                "All configured files are unchanged and present on the calculator.",
                "TI-84 Evo Project Is Up to Date",
                arrayOf("Push Anyway", "Cancel"),
                1,
                Messages.getInformationIcon(),
            )
            if (choice != 0) return
            pending = configured
            pushAnyway = true
        }

        val uploadMode = when {
            resolvedProject.alwaysPushAll -> "always rebuild"
            pushAnyway -> "push anyway"
            else -> "incremental synchronization"
        }
        showStatus("Pushing ${pending.size} project files ($uploadMode)…", StatusKind.WORKING)
        uploadProgress.minimum = 0
        uploadProgress.maximum = pending.size
        uploadProgress.value = 0
        uploadProgress.string = "Preparing ${pending.size} file(s)…"
        uploadProgress.isVisible = true
        output.text = buildString {
            appendLine("Uploading project files in one calculator session ($uploadMode):")
            pending.forEach { appendLine("• ${it.entry.programName} → ${if (it.entry.archived) "Archive" else "RAM"}") }
            val skipped = configured.size - pending.size
            if (skipped > 0) appendLine("Skipping $skipped unchanged file(s) already present on the calculator.")
        }

        service.uploadPythonProject(
            pending.map { EvoPythonTransfer.Program(it.entry.programName, it.source, it.entry.archived) },
            onProgress = { upload, completed, total ->
                val resolved = pending[completed - 1]
                runCatching { EvoProjectUploadState.markUploaded(root, resolved.entry, resolved.source) }
                    .onFailure { e ->
                        onEdt {
                            output.text = buildString {
                                append(output.text)
                                appendLine("Warning: could not save upload state for ${resolved.entry.programName}: ${e.message ?: e.javaClass.simpleName}")
                            }
                        }
                    }
                onEdt {
                    uploadProgress.value = completed
                    uploadProgress.string = "Uploaded $completed/$total: ${upload.programName}"
                }
            },
        ) { result ->
            onEdt {
                uploadProgress.isVisible = false
                result.onSuccess { upload ->
                    showStatus("Pushed ${upload.uploads.size} project files", StatusKind.CONNECTED)
                    output.text = buildString {
                        appendLine("Project upload successful")
                        upload.uploads.forEach { file ->
                            appendLine("${file.programName}: ${file.sourceBytes} source bytes, ${file.packets} packets")
                        }
                        appendLine("Total source: ${upload.sourceBytes} bytes")
                        appendLine("Total transfer payload: ${upload.payloadBytes} bytes")
                        appendLine("Total Kermit packets: ${upload.packets}")
                        append("Unchanged files skipped: ${configured.size - pending.size}")
                    }
                    refreshDirectoryAfterOperation("Pushed ${upload.uploads.size} project files")
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun pullProject() {
        val root = projectRoot() ?: return
        showStatus("Pulling Python project from calculator…", StatusKind.WORKING)
        uploadProgress.minimum = 0
        uploadProgress.maximum = 1
        uploadProgress.value = 0
        uploadProgress.string = "Reading calculator directory…"
        uploadProgress.isVisible = true
        output.text = "Downloading every Python program from the calculator…"

        service.pullPythonProject(
            onProgress = { program, completed, total ->
                onEdt {
                    uploadProgress.maximum = total
                    uploadProgress.value = completed
                    uploadProgress.string = "Downloaded $completed/$total: ${program.programName}"
                }
            },
        ) { result ->
            onEdt {
                uploadProgress.isVisible = false
                result.onSuccess { pulled ->
                    val plan = runCatching {
                        val existing = readProjectConfigurationIfPresent(root)
                        EvoProjectPull.plan(root, pulled.programs, existing) { path -> currentProjectSource(path) }
                    }.getOrElse {
                        showFailure(it)
                        return@onSuccess
                    }
                    val answer = Messages.showDialog(
                        project,
                        buildString {
                            appendLine("Pull ${plan.targets.size} Python program(s) into this project?")
                            appendLine()
                            plan.targets.take(12).forEach { target ->
                                appendLine(
                                    "• ${target.entry.programName} → ${target.entry.sourcePath} " +
                                        "(${if (target.entry.archived) "Archive" else "RAM"})",
                                )
                            }
                            if (plan.targets.size > 12) appendLine("• …and ${plan.targets.size - 12} more")
                            if (plan.conflicts.isNotEmpty()) {
                                appendLine()
                                appendLine(
                                    "This will overwrite local changes in: " +
                                        plan.conflicts.joinToString { it.entry.sourcePath },
                                )
                            }
                            appendLine()
                            append("The project manifest will be updated to match the calculator.")
                        },
                        "Pull Python Project from TI-84 Evo",
                        arrayOf(if (plan.conflicts.isEmpty()) "Save Project" else "Overwrite and Pull", "Cancel"),
                        1,
                        if (plan.conflicts.isEmpty()) Messages.getQuestionIcon() else Messages.getWarningIcon(),
                    )
                    if (answer != 0) {
                        showStatus("Project pull cancelled", StatusKind.READY)
                        return@onSuccess
                    }

                    FileDocumentManager.getInstance().saveAllDocuments()
                    ApplicationManager.getApplication().executeOnPooledThread {
                        runCatching {
                            EvoProjectPull.write(plan, overwrite = plan.conflicts.isNotEmpty())
                            val refreshed = plan.targets.map { it.path } + root.resolve(EvoProjectManifest.FILE_NAME)
                            refreshed.forEach(LocalFileSystem.getInstance()::refreshAndFindFileByNioFile)
                        }.onSuccess {
                            onEdt {
                                showStatus("Pulled ${plan.targets.size} Python project files", StatusKind.CONNECTED)
                                output.text = buildString {
                                    appendLine("Project pull successful")
                                    plan.targets.forEach { target ->
                                        appendLine("• ${target.entry.programName} → ${target.entry.sourcePath}")
                                    }
                                    appendLine("Total source: ${pulled.sourceBytes} bytes")
                                    appendLine("Total transfer payload: ${pulled.payloadBytes} bytes")
                                    append("Saved ${EvoProjectManifest.FILE_NAME}; the next normal push will treat these files as synchronized.")
                                }
                            }
                        }.onFailure { error ->
                            onEdt { showFailure(error) }
                        }
                    }
                }.onFailure(::showFailure)
            }
        }
    }

    private fun readProjectConfigurationIfPresent(root: Path): EvoProjectManifest.Configuration? =
        ApplicationManager.getApplication().runReadAction<EvoProjectManifest.Configuration?> {
            val manifestPath = root.resolve(EvoProjectManifest.FILE_NAME)
            val manifestFile = LocalFileSystem.getInstance().findFileByNioFile(manifestPath) ?: return@runReadAction null
            val text = FileDocumentManager.getInstance().getDocument(manifestFile)?.text
                ?: String(manifestFile.contentsToByteArray(), StandardCharsets.UTF_8)
            EvoProjectManifest.parseConfiguration(text)
        }

    private fun currentProjectSource(path: Path): String? =
        LocalFileSystem.getInstance().findFileByNioFile(path)?.let { file ->
            if (file.isDirectory) null else FileDocumentManager.getInstance().getDocument(file)?.text
                ?: String(file.contentsToByteArray(), StandardCharsets.UTF_8)
        }

    private data class ResolvedProjectProgram(
        val entry: EvoProjectManifest.Entry,
        val source: String,
    )

    private data class ResolvedProject(
        val programs: List<ResolvedProjectProgram>,
        val alwaysPushAll: Boolean,
    )

    private fun readProjectPrograms(root: Path): ResolvedProject =
        ApplicationManager.getApplication().runReadAction<ResolvedProject> {
            val manifestPath = root.resolve(EvoProjectManifest.FILE_NAME)
            val manifestFile = LocalFileSystem.getInstance().findFileByNioFile(manifestPath)
                ?: throw java.nio.file.NoSuchFileException(manifestPath.toString())
            val manifestText = FileDocumentManager.getInstance().getDocument(manifestFile)?.text
                ?: String(manifestFile.contentsToByteArray(), StandardCharsets.UTF_8)

            val configuration = EvoProjectManifest.parseConfiguration(manifestText)
            val programs = configuration.entries.map { entry ->
                val sourcePath = EvoProjectManifest.resolveSource(root, entry.sourcePath)
                val sourceFile = LocalFileSystem.getInstance().findFileByNioFile(sourcePath)
                    ?: throw EvoProjectManifest.ConfigurationException(
                        "Declared source file does not exist: ${entry.sourcePath}",
                    )
                if (sourceFile.isDirectory) {
                    throw EvoProjectManifest.ConfigurationException(
                        "Declared source is not a file: ${entry.sourcePath}",
                    )
                }
                val source = FileDocumentManager.getInstance().getDocument(sourceFile)?.text
                    ?: String(sourceFile.contentsToByteArray(), StandardCharsets.UTF_8)
                ResolvedProjectProgram(entry, source)
            }
            ResolvedProject(programs, configuration.alwaysPushAll)
        }

    private fun projectRoot(): Path? {
        val basePath = project.basePath
        if (basePath == null) {
            Messages.showWarningDialog(project, "Open a project directory first.", "TI-84 Evo")
            return null
        }
        return Path.of(basePath).toAbsolutePath().normalize()
    }

    internal fun showAbout() {
        val dialog = EvoAboutDialog(
            project = project,
            versionStatus = versionStatus,
            pluginId = PLUGIN_ID,
            mischiefMode = mischiefMode,
            retryMarketplaceValidation = marketplaceService::retryNow,
            debugInfo = { status ->
                EvoDebugInfo.create(status, applicationSettings.snapshot(mischiefMode), mischiefMode)
            },
            openInstalledPluginDirectory = ::openInstalledPluginDirectory,
        )
        aboutDialog = dialog
        if (versionStatus.isUnverified) marketplaceService.retryNow()
        try {
            dialog.show()
        } finally {
            if (aboutDialog === dialog) aboutDialog = null
        }
    }

    private fun openInstalledPluginDirectory() {
        runCatching {
            val path = EvoBuildInfo.installationDirectory
                ?: error("The installed plugin directory could not be determined.")
            val directory = path.takeIf(Files::isDirectory) ?: path.parent
            require(directory != null && Files.isDirectory(directory)) {
                "The installed plugin directory is unavailable."
            }
            require(Desktop.isDesktopSupported()) { "The operating system file manager is unavailable." }
            Desktop.getDesktop().open(directory.toFile())
        }.onFailure {
            Messages.showErrorDialog(project, it.message ?: "Could not open the plugin directory.", "TI-84 Evo")
        }
    }

    override fun dispose() = Unit

    private fun showFailure(error: Throwable) {
        uploadProgress.isVisible = false
        showStatus("Operation failed", StatusKind.ERROR)
        output.text = when (error) {
            is EvoPythonTransfer.ProjectUploadException -> {
                buildString {
                    appendLine(error.message)
                    if (error.completedUploads.isNotEmpty()) {
                        appendLine("Already uploaded: ${error.completedUploads.joinToString { it.programName }}")
                    }
                    appendLine()
                    append(error.cause?.stackTraceToString() ?: error.stackTraceToString())
                }
            }
            is EvoVariableDeleteException -> {
                buildString {
                    appendLine(error.message)
                    if (error.deletedEntries.isNotEmpty()) {
                        appendLine("Already deleted: ${error.deletedEntries.joinToString { it.name }}")
                    }
                    if (error.clearedEntries.isNotEmpty()) {
                        appendLine("Already cleared: ${error.clearedEntries.joinToString { it.name }}")
                    }
                    val action = if (isPersistentBuiltInList(error.failedEntry)) "clear" else "delete"
                    appendLine("Failed to $action: ${error.failedEntry.name}")
                    appendLine()
                    append(error.cause?.stackTraceToString() ?: error.stackTraceToString())
                }
            }
            is EvoVariableArchiveException -> {
                buildString {
                    appendLine(error.message)
                    if (error.archivedEntries.isNotEmpty()) {
                        appendLine("Already archived: ${error.archivedEntries.joinToString { it.name }}")
                    }
                    appendLine("Failed variable: ${error.failedEntry.name}")
                    appendLine()
                    append(error.cause?.stackTraceToString() ?: error.stackTraceToString())
                }
            }
            is EvoPythonProjectPuller.ProjectPullException -> {
                buildString {
                    appendLine(error.message)
                    if (error.completedPrograms.isNotEmpty()) {
                        appendLine("Already downloaded: ${error.completedPrograms.joinToString { it.programName }}")
                    }
                    appendLine("Failed program: ${error.failedEntry.name}")
                    appendLine()
                    append(error.cause?.stackTraceToString() ?: error.stackTraceToString())
                }
            }
            else -> error.stackTraceToString()
        }
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            ?: error.javaClass.simpleName
        val unreachable = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.lowercase() }
            .any { "no ti-84 evo" in it || "failed to open" in it || "timed out" in it }
        Messages.showErrorDialog(
            project,
            if (unreachable) {
                "The calculator could not be reached. Check the USB connection, wake the calculator, " +
                    "close other calculator-link software, and try again.\n\n$message"
            } else {
                message
            },
            if (unreachable) "TI-84 Evo Not Reachable" else "TI-84 Evo Operation Failed",
        )
    }

    private fun formatValue(value: Any?): String = when (value) {
        is ByteArray -> "bytes[${value.size}]"
        else -> value.toString()
    }

    private fun showStatus(text: String, kind: StatusKind) {
        status.text = text
        status.icon = when (kind) {
            StatusKind.IDLE -> AllIcons.General.Information
            StatusKind.WORKING -> AllIcons.Actions.Refresh
            StatusKind.READY -> AllIcons.General.GreenCheckmark
            StatusKind.CONNECTED -> AllIcons.General.GreenCheckmark
            StatusKind.ERROR -> AllIcons.General.Error
        }
    }

    private fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater { action() }
    }

    private enum class StatusKind {
        IDLE,
        WORKING,
        READY,
        CONNECTED,
        ERROR,
    }
}
