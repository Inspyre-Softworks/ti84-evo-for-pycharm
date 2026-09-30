package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.service.EvoMischiefMode
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Modeless developer console for bundled, opt-in diagnostics. */
internal class EvoDeveloperToolsDialog(private val project: Project) : DialogWrapper(project) {
    private val output = JBTextArea().apply {
        isEditable = false
        lineWrap = false
    }
    private val usbArguments = JBTextField("--help")
    private val stopButton = JButton("Stop").apply {
        isEnabled = false
        addActionListener { stopProcess() }
    }
    private var process: Process? = null
    private var toolDirectory: Path? = null
    private var launchToken = 0
    private var launchPending = false
    private var continuousScreen = false
    private var gracefulStopRequested = false
    private var closed = false

    init {
        title = "TI-84 Evo Developer Utilities"
        isModal = false
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(12)
        preferredSize = Dimension(730, 460)
        add(JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(JBLabel("Launch one utility at a time. Output appears below; Stop ends the running utility."))
            add(buttonRow(
                JButton("15-second benchmark").apply {
                    toolTipText = "Measure live capture rate for 15 seconds; uses the calculator serial port"
                    addActionListener { launch(Tool.SCREEN, listOf("--seconds", "15", "--preview")) }
                },
                JButton("Live screen preview").apply {
                    toolTipText = "Keep showing the calculator screen until the preview closes or Stop is pressed"
                    addActionListener { launch(Tool.SCREEN, listOf("--continuous", "--preview")) }
                },
                JButton("Record live screen (MP4)").apply {
                    toolTipText = "Record PNG frames and timing data, then make an MP4 when stopped"
                    addActionListener { recordLiveScreen() }
                },
            ))
            add(buttonRow(
                JButton("SmartPad key observer").apply {
                    addActionListener { launch(Tool.MACROPAD, listOf("--observe")) }
                },
                JButton("SmartPad macro pad").apply {
                    toolTipText = "GRAPH runs and TRACE debugs in PyCharm by default"
                    addActionListener { launch(Tool.MACROPAD, emptyList()) }
                },
            ))
            add(buttonRow(
                JBLabel("SmartPad USB arguments:"),
                usbArguments.apply { preferredSize = Dimension(370, preferredSize.height) },
                JButton("Run USB diagnostic").apply {
                    addActionListener {
                        runCatching { splitArguments(usbArguments.text) }
                            .onSuccess { launch(Tool.USB, it) }
                            .onFailure { showFailure(it) }
                    }
                },
            ))
            add(buttonRow(stopButton))
        }, BorderLayout.NORTH)
        add(JBScrollPane(output), BorderLayout.CENTER)
    }

    private fun buttonRow(vararg components: JComponent) = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
        components.forEach(::add)
    }

    private fun launch(tool: Tool, arguments: List<String>) {
        if (!EvoMischiefMode.isActive()) {
            Messages.showWarningDialog(project, "Activate Marauders Lock to use developer utilities.", title)
            return
        }
        if (!isWindows() && tool == Tool.MACROPAD) {
            Messages.showWarningDialog(project, "The SmartPad macro pad requires Windows.", title)
            return
        }
        if (launchPending || process?.isAlive == true) {
            Messages.showWarningDialog(project, "Stop the current utility and wait for it to finish first.", title)
            return
        }
        launchPending = true
        launchToken++
        val token = launchToken
        val command = runCatching { commandFor(tool, arguments) }.getOrElse {
            launchPending = false
            showFailure(it)
            return
        }
        output.append("\n> ${command.joinToString(" ") { quoteForDisplay(it) }}\n")
        val workingDirectory = project.basePath?.let(::File)
        Thread({
            runCatching {
                val started = ProcessBuilder(command)
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start()
                SwingUtilities.invokeLater {
                    if (closed || token != launchToken) {
                        started.destroy()
                    } else {
                        process = started
                        continuousScreen = tool == Tool.SCREEN && "--continuous" in arguments
                        gracefulStopRequested = false
                        launchPending = false
                        stopButton.isEnabled = true
                    }
                }
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> SwingUtilities.invokeLater { output.append("$line\n") } }
                }
                val exitCode = started.waitFor()
                SwingUtilities.invokeLater {
                    if (closed) return@invokeLater
                    output.append("Exited with code $exitCode\n")
                    if (process === started) {
                        process = null
                        continuousScreen = false
                        gracefulStopRequested = false
                        stopButton.isEnabled = false
                    }
                }
            }.onFailure { SwingUtilities.invokeLater {
                if (token == launchToken) launchPending = false
                if (!closed) showFailure(it)
            } }
        }, "TI-84 Evo ${tool.label}").apply { isDaemon = true }.start()
    }

    private fun recordLiveScreen() {
        val chooser = JFileChooser(project.basePath ?: System.getProperty("user.home")).apply {
            dialogTitle = "Choose a folder for the screen recording"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return
        val parent = chooser.selectedFile.toPath()
        val name = "ti84-evo-screen-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        var directory = parent.resolve(name)
        var suffix = 2
        while (Files.exists(directory)) {
            directory = parent.resolve("$name-$suffix")
            suffix++
        }
        launch(Tool.SCREEN, listOf("--continuous", "--preview", "--save-dir", directory.toString(), "--mp4"))
    }

    private fun commandFor(tool: Tool, arguments: List<String>): List<String> {
        val directory = toolDirectory ?: extractTools().also { toolDirectory = it }
        return when (tool) {
            Tool.SCREEN -> {
                val java = Path.of(System.getProperty("java.home"), "bin", if (isWindows()) "java.exe" else "java")
                val lib = EvoBuildInfo.installationDirectory?.resolve("lib")
                    ?: error("The installed plugin library directory could not be found.")
                require(Files.isDirectory(lib)) { "The installed plugin libraries could not be found at $lib" }
                listOf(java.toString(), "--enable-native-access=ALL-UNNAMED", "--class-path", classpathWildcard(lib),
                    directory.resolve("EvoScreenBenchmark.java").toString()) + arguments
            }
            Tool.MACROPAD -> listOf("py", directory.resolve("smartpad_macropad.py").toString()) + arguments
            Tool.USB -> listOf(if (isWindows()) "py" else "python3", directory.resolve("smartpad_usb.py").toString()) + arguments
        }
    }

    private fun extractTools(): Path {
        val directory = Files.createTempDirectory("ti84-evo-devtools-")
        try {
            for (name in TOOL_FILES) {
                val source = javaClass.getResourceAsStream("/utils/$name")
                    ?: error("Bundled utility $name is missing from the plugin.")
                source.use { Files.copy(it, directory.resolve(name)) }
            }
            return directory
        } catch (failure: Throwable) {
            for (name in TOOL_FILES) {
                Files.deleteIfExists(directory.resolve(name))
            }
            Files.deleteIfExists(directory)
            throw failure
        }
    }

    private fun stopProcess() {
        launchToken++
        launchPending = false
        val current = process
        if (current != null && current.isAlive) {
            if (continuousScreen) {
                if (!gracefulStopRequested) {
                    gracefulStopRequested = true
                    runCatching {
                        current.outputStream.write('\n'.code)
                        current.outputStream.flush()
                        output.append("Stopping capture and finalizing recording...\n")
                    }.onFailure { current.destroy() }
                }
            } else {
                current.destroy()
            }
        }
        stopButton.isEnabled = false
    }

    private fun showFailure(error: Throwable) {
        output.append("${error.message ?: error.javaClass.simpleName}\n")
    }

    override fun doOKAction() {
        closeTools()
        super.doOKAction()
    }

    override fun doCancelAction() {
        closeTools()
        super.doCancelAction()
    }

    private fun closeTools() {
        closed = true
        stopProcess()
        process?.let { current ->
            if (current.isAlive && !current.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                current.destroyForcibly()
                current.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
        toolDirectory?.let { directory ->
            for (name in TOOL_FILES) {
                runCatching { Files.deleteIfExists(directory.resolve(name)) }
            }
            runCatching { Files.deleteIfExists(directory) }
        }
    }

    private enum class Tool(val label: String) {
        SCREEN("screen benchmark"), MACROPAD("SmartPad macro pad"), USB("SmartPad USB diagnostic")
    }

    companion object {
        private val TOOL_FILES = listOf(
            "EvoScreenBenchmark.java", "smartpad_macropad.py", "smartpad_usb.py", "requirements-smartpad.txt",
        )
        private fun isWindows() = System.getProperty("os.name").contains("Windows", ignoreCase = true)

        internal fun classpathWildcard(libraryDirectory: Path): String =
            libraryDirectory.toString() + File.separator + "*"

        private fun quoteForDisplay(value: String) = if (value.any(Char::isWhitespace)) "\"$value\"" else value

        /** Splits editable diagnostic arguments without invoking a shell. */
        internal fun splitArguments(text: String): List<String> {
            val result = mutableListOf<String>()
            val token = StringBuilder()
            var quote: Char? = null
            var started = false
            for (char in text) {
                when {
                    quote != null && char == quote -> quote = null
                    quote == null && (char == '\'' || char == '"') -> {
                        quote = char
                        started = true
                    }
                    quote == null && char.isWhitespace() -> {
                        if (started) {
                            result += token.toString()
                            token.setLength(0)
                            started = false
                        }
                    }
                    else -> {
                        token.append(char)
                        started = true
                    }
                }
            }
            require(quote == null) { "Close the quoted argument before running the diagnostic." }
            if (started) result += token.toString()
            return result
        }
    }
}
