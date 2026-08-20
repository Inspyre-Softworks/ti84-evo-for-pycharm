package com.inspyresoftworks.ti84evo.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.protocol.EvoPythonPayload
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import com.inspyresoftworks.ti84evo.service.EvoDeviceService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.ImageIcon
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * TI-84 Evo tool-window UI.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = project.getService(EvoDeviceService::class.java)
    private val status = JBLabel("Not checked", AllIcons.General.Information, JBLabel.LEADING)
    private val output = JBTextArea().apply {
        isEditable = false
        lineWrap = false
    }
    private val screen = JBLabel("No screenshot", JBLabel.CENTER).apply {
        preferredSize = Dimension(640, 480)
    }

    init {
        border = JBUI.Borders.empty(8)

        val toolbar = EvoToolWindowToolbar.create(
            this,
            EvoToolWindowActions(
                refresh = ::refresh,
                readAttributes = ::readAttributes,
                captureScreen = ::captureScreen,
                uploadCurrentPython = ::uploadCurrentPython,
                configureProject = ::configureProject,
                pushProject = ::pushProject,
            ),
        )
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(8)
            add(toolbar.component, BorderLayout.NORTH)
            add(status.apply { border = JBUI.Borders.empty(5, 4, 0, 4) }, BorderLayout.SOUTH)
        }

        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JBScrollPane(screen),
            JBScrollPane(output),
        ).apply {
            resizeWeight = 0.72
        }

        add(header, BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)
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
                    output.text = attributes.entries.joinToString("\n") { (key, value) -> "$key: ${formatValue(value)}" }
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
                    output.text = capture.metadata.entries.joinToString("\n") { (key, value) -> "$key: ${formatValue(value)}" }
                }.onFailure { showFailure(it) }
            }
        }
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

        showStatus("Uploading ${file.name}…", StatusKind.WORKING)
        output.text = "Packaging ${file.name} as ${programName.uppercase()}…"

        service.uploadPython(source, programName) { result ->
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
        // PyCharm 2026.2's VFS chooser can drop known file extensions while
        // resolving a multi-selection (for example TAYAPPS.py -> TAYAPPS),
        // then report that the real files cannot be located. JFileChooser
        // returns the selected filesystem paths directly and avoids that lossy
        // VFS display-name round trip.
        val chooser = JFileChooser(root.toFile()).apply {
            dialogTitle = "Select TI-84 Evo Python Project Files"
            isMultiSelectionEnabled = true
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Python source files (*.py)", "py")
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFiles
            .takeIf { it.isNotEmpty() }
            ?: chooser.selectedFile?.let { arrayOf(it) }
            ?: emptyArray()
        if (selected.isEmpty()) return

        val manifestText = runCatching {
            val relativePaths = selected.map { file ->
                val path = file.toPath().toAbsolutePath().normalize()
                if (!path.startsWith(root)) {
                    throw EvoProjectManifest.ConfigurationException(
                        "Project files must be inside ${root.toAbsolutePath()}: $path",
                    )
                }
                root.relativize(path).toString().replace('\\', '/')
            }
            EvoProjectManifest.render(relativePaths)
        }.getOrElse {
            showFailure(it)
            return
        }

        val manifestPath = root.resolve(EvoProjectManifest.FILE_NAME)
        if (Files.exists(manifestPath)) {
            val answer = Messages.showYesNoDialog(
                project,
                "Replace the existing ${EvoProjectManifest.FILE_NAME} file?",
                "Configure TI-84 Evo Project",
                Messages.getWarningIcon(),
            )
            if (answer != Messages.YES) return
        }

        runCatching {
            val manifestFile = ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                Files.writeString(manifestPath, manifestText, StandardCharsets.UTF_8)
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(manifestPath)
                    ?: error("Created manifest could not be opened: $manifestPath")
            }
            FileEditorManager.getInstance(project).openFile(manifestFile, true)
        }.onSuccess {
            showStatus("Project configured: ${selected.size} files", StatusKind.READY)
            output.text = buildString {
                appendLine("Created ${EvoProjectManifest.FILE_NAME}")
                appendLine("${selected.size} Python files will be pushed together.")
                append("Edit calculator names in the manifest if needed, then press Push Project.")
            }
        }.onFailure { showFailure(it) }
    }

    private fun pushProject() {
        val root = projectRoot() ?: return
        val programs = runCatching { readProjectPrograms(root) }.getOrElse { error ->
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

        showStatus("Pushing ${programs.size} project files…", StatusKind.WORKING)
        output.text = buildString {
            appendLine("Uploading in one calculator session:")
            programs.forEach { appendLine("• ${it.programName}") }
        }

        service.uploadPythonProject(programs) { result ->
            onEdt {
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
                        append("Target: RAM")
                    }
                }.onFailure { showFailure(it) }
            }
        }
    }

    private fun readProjectPrograms(root: Path): List<EvoPythonTransfer.Program> =
        ApplicationManager.getApplication().runReadAction<List<EvoPythonTransfer.Program>> {
            val manifestPath = root.resolve(EvoProjectManifest.FILE_NAME)
            val manifestFile = LocalFileSystem.getInstance().findFileByNioFile(manifestPath)
                ?: throw java.nio.file.NoSuchFileException(manifestPath.toString())
            val manifestText = FileDocumentManager.getInstance().getDocument(manifestFile)?.text
                ?: String(manifestFile.contentsToByteArray(), StandardCharsets.UTF_8)

            EvoProjectManifest.parse(manifestText).map { entry ->
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
                EvoPythonTransfer.Program(entry.programName, source)
            }
        }

    private fun projectRoot(): Path? {
        val basePath = project.basePath
        if (basePath == null) {
            Messages.showWarningDialog(project, "Open a project directory first.", "TI-84 Evo")
            return null
        }
        return Path.of(basePath).toAbsolutePath().normalize()
    }

    private fun showFailure(error: Throwable) {
        showStatus("Operation failed", StatusKind.ERROR)
        output.text = if (error is EvoPythonTransfer.ProjectUploadException) {
            buildString {
                appendLine(error.message)
                if (error.completedUploads.isNotEmpty()) {
                    appendLine("Already uploaded: ${error.completedUploads.joinToString { it.programName }}")
                }
                appendLine()
                append(error.cause?.stackTraceToString() ?: error.stackTraceToString())
            }
        } else {
            error.stackTraceToString()
        }
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
