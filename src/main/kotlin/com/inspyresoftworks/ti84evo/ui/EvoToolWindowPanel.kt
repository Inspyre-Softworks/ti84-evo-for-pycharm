package com.inspyresoftworks.ti84evo.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.ide.plugins.PluginManagerCore
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
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.protocol.EvoVariableDeleteException
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import com.inspyresoftworks.ti84evo.project.EvoProjectUploadState
import com.inspyresoftworks.ti84evo.service.EvoDeviceService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Image
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JSplitPane
import javax.swing.table.DefaultTableModel

/**
 * TI-84 Evo tool-window UI.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private companion object {
        const val PLUGIN_ID = "com.inspyresoftworks.ti84evo"
    }

    private val service = project.getService(EvoDeviceService::class.java)
    private val installedPluginVersion =
        PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "unknown"
    private val status = JBLabel("Not checked", AllIcons.General.Information, JBLabel.LEADING)
    private val version = JBLabel(
        "v$installedPluginVersion",
        JBLabel.TRAILING,
    ).apply {
        toolTipText = "Installed TI-84 Evo plugin version"
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
    private var deletionInProgress = false
    private val deleteSelectedButton = JButton("Delete selected", AllIcons.General.Remove).apply {
        isEnabled = false
        toolTipText = "Delete the selected calculator files after confirmation"
        addActionListener { deleteSelectedFiles() }
    }
    private val screenPane = JBScrollPane(screen)
    private val directoryPane = JBScrollPane(directoryTable)
    private val directoryContent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(directoryPane, BorderLayout.CENTER)
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 4)).apply {
                isOpaque = false
                add(deleteSelectedButton)
            },
            BorderLayout.SOUTH,
        )
    }
    private val contentTabs = JBTabbedPane().apply {
        addTab("Screen", screenPane)
        addTab("Calculator Files", directoryContent)
    }

    init {
        border = JBUI.Borders.empty(8)
        directoryTable.selectionModel.addListSelectionListener {
            updateDeleteSelectedButtonState()
        }

        val toolbar = EvoToolWindowToolbar.create(
            this,
            EvoToolWindowActions(
                refresh = ::refresh,
                readAttributes = ::readAttributes,
                browseFiles = ::browseFiles,
                captureScreen = ::captureScreen,
                uploadCurrentPython = ::uploadCurrentPython,
                configureProject = ::configureProject,
                pushProject = ::pushProject,
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
        refresh()
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
        showStatus("Reading calculator directory…", StatusKind.WORKING)
        service.readDirectory { result ->
            onEdt {
                result.onSuccess { entries ->
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
                    contentTabs.selectedComponent = directoryContent
                    showStatus(
                        "Connected — ${entries.size} calculator files",
                        StatusKind.CONNECTED,
                    )
                    val ramEntries = entries.filterNot { it.archived }
                    val archiveEntries = entries.filter { it.archived }
                    output.text = buildString {
                        appendLine("Calculator directory")
                        appendLine("Variables: ${entries.size}")
                        appendLine("RAM: ${ramEntries.size} variables, ${ramEntries.sumOf { it.size }} bytes")
                        appendLine("Archive: ${archiveEntries.size} variables, ${archiveEntries.sumOf { it.size }} bytes")
                        if (entries.isEmpty()) append("No variables were returned by the directory resource.")
                    }.trimEnd()
                    updateDeleteSelectedButtonState()
                }.onFailure { showFailure(it) }
            }
        }
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
                    val scale = minOf(2.0, 640.0 / image.width, 480.0 / image.height)
                    val scaled = image.getScaledInstance(
                        (image.width * scale).toInt(),
                        (image.height * scale).toInt(),
                        Image.SCALE_FAST,
                    )
                    screen.text = null
                    screen.icon = ImageIcon(scaled)
                    contentTabs.selectedComponent = screenPane
                    output.text = capture.metadata.entries.joinToString("\n") { (key, value) -> "$key: ${formatValue(value)}" }
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun deleteSelectedFiles() {
        if (deletionInProgress) return
        val selectedEntries = directoryTable.selectedRows
            .map(directoryTable::convertRowIndexToModel)
            .distinct()
            .sorted()
            .map(directoryEntries::get)
        if (selectedEntries.isEmpty()) return

        val answer = Messages.showYesNoDialog(
            project,
            buildString {
                appendLine(
                    if (selectedEntries.size == 1) {
                        "Delete ${selectedEntries.single().name} from the calculator?"
                    } else {
                        "Delete ${selectedEntries.size} selected files from the calculator?"
                    },
                )
                appendLine()
                selectedEntries.take(10).forEach { entry ->
                    appendLine("• ${entry.name} (${entry.typeName}, ${entry.location})")
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
        output.text = "Deleting ${selectedEntries.joinToString { it.name }}…"
        service.deleteVariables(selectedEntries) { result ->
            onEdt {
                try {
                    result.onSuccess { deletedEntries ->
                        removeDirectoryEntries(deletedEntries)
                        showStatus("Deleted ${deletedEntries.size} calculator file(s)", StatusKind.CONNECTED)
                        output.text = buildString {
                            appendLine("Delete successful")
                            deletedEntries.forEach {
                                appendLine("• ${it.name} (${it.typeName}, ${it.size} bytes, ${it.location})")
                            }
                        }
                    }.onFailure { error ->
                        if (error is EvoVariableDeleteException) {
                            removeDirectoryEntries(error.deletedEntries)
                        }
                        showFailure(error)
                    }
                } finally {
                    deletionInProgress = false
                    updateDeleteSelectedButtonState()
                }
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

        var pending = if (resolvedProject.alwaysPushAll) {
            configured
        } else {
            val pendingPairs = EvoProjectUploadState.pending(
                root,
                configured.map { it.entry to it.source },
            )
            val pendingPaths = pendingPairs.mapTo(mutableSetOf()) { it.first.sourcePath }
            configured.filter { it.entry.sourcePath in pendingPaths }
        }
        if (pending.isEmpty()) {
            showStatus("Project is up to date", StatusKind.READY)
            output.text = "No changed project files to upload."
            val choice = Messages.showDialog(
                project,
                "All configured files are already up to date.",
                "TI-84 Evo Project Is Up to Date",
                arrayOf("Push Anyway", "Cancel"),
                1,
                Messages.getInformationIcon(),
            )
            if (choice != 0) return
            pending = configured
        }

        val uploadMode = when {
            resolvedProject.alwaysPushAll -> "always rebuild"
            pending.size == configured.size -> "push anyway"
            else -> "incremental"
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
            if (skipped > 0) appendLine("Skipping $skipped unchanged file(s).")
        }

        service.uploadPythonProject(
            pending.map { EvoPythonTransfer.Program(it.entry.programName, it.source, it.entry.archived) },
            onProgress = { upload, completed, total ->
                val resolved = pending[completed - 1]
                runCatching { EvoProjectUploadState.markUploaded(root, resolved.entry, resolved.source) }
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
                }.onFailure { showFailure(it) }
            }
        }
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
        val buildType = if (installedPluginVersion.endsWith("-SNAPSHOT")) "Development snapshot" else "Release"
        Messages.showInfoMessage(
            project,
            buildString {
                appendLine("TI-84 Evo for PyCharm")
                appendLine()
                appendLine("Version: $installedPluginVersion")
                appendLine("Build: $buildType")
                append("Plugin ID: $PLUGIN_ID")
            },
            "About TI-84 Evo",
        )
    }

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
                    appendLine("Failed variable: ${error.failedEntry.name}")
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
