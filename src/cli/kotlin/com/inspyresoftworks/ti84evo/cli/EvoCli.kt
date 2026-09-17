package com.inspyresoftworks.ti84evo.cli

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import com.inspyresoftworks.ti84evo.project.EvoProjectPull
import com.inspyresoftworks.ti84evo.project.EvoProjectUploadState
import com.inspyresoftworks.ti84evo.protocol.EvoLink
import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectPuller
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.protocol.EvoVariableDeleteException
import com.inspyresoftworks.ti84evo.protocol.EvoVariableTransfer
import com.inspyresoftworks.ti84evo.protocol.isPersistentBuiltInList
import com.inspyresoftworks.ti84evo.transport.EvoSerialTransport
import java.nio.charset.StandardCharsets
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

/** Standalone sender used by PowerShell and Windows Explorer context menus. */
object EvoCli {
    @JvmStatic
    fun main(args: Array<String>) {
        System.setOut(PrintStream(System.out, true, StandardCharsets.UTF_8))
        System.setErr(PrintStream(System.err, true, StandardCharsets.UTF_8))
        try {
            when (args.firstOrNull()?.lowercase()) {
                "send" -> send(args.drop(1))
                "pull" -> pull(args.drop(1))
                "archive" -> archiveFiles(args.drop(1))
                "delete", "rm" -> deleteFiles(args.drop(1))
                "list-files", "list" -> listFiles()
                "install-context-menu" -> installContextMenu()
                "uninstall-context-menu" -> uninstallContextMenu()
                "help", "--help", "-h", null -> usage()
                else -> error("Unknown command: ${args.first()}")
            }
        } catch (error: Throwable) {
            Terminal.error(error.message ?: error.javaClass.simpleName)
            exitProcess(1)
        }
    }

    private fun listFiles() {
        Terminal.info("CONNECT", "Looking for a TI-84 Evo over USB…")
        val entries = EvoSerialTransport.auto().use { transport ->
            transport.open()
            Terminal.success("Connected to ${transport.description}")
            EvoLink(transport).getDirectory()
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.type }, { it.archived }))

        Terminal.header("CALCULATOR FILES", "${entries.size} variable(s)")
        if (entries.isEmpty()) {
            Terminal.muted("  No calculator variables were returned.")
            return
        }

        println("  ${"NAME".padEnd(12)} ${"TYPE".padEnd(22)} ${"SIZE".padStart(10)}  MEMORY")
        entries.forEach { entry ->
            val type = "${entry.typeName} (${entry.type})"
            println(
                "  ${entry.name.padEnd(12)} ${type.padEnd(22)} ${formatBytes(entry.size).padStart(10)}  ${entry.location}",
            )
        }
    }

    private fun send(arguments: List<String>) {
        var force = false
        var targetOverride: Boolean? = null
        var projectRoot = Paths.get("").toAbsolutePath().normalize()
        val pathArguments = mutableListOf<String>()
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "--all", "--force", "--always-rebuild" -> force = true
                "--archive" -> targetOverride = true
                "--ram" -> targetOverride = false
                "--project" -> {
                    index++
                    require(index < arguments.size) { "--project requires a directory" }
                    projectRoot = Paths.get(arguments[index]).toAbsolutePath().normalize()
                }
                else -> pathArguments += argument
            }
            index++
        }

        if (pathArguments.size == 1) {
            val supplied = Paths.get(pathArguments.single()).toAbsolutePath().normalize()
            if (supplied.isDirectory() && supplied.resolve(EvoProjectManifest.FILE_NAME).isRegularFile()) {
                projectRoot = supplied
                pathArguments.clear()
            }
        }

        val configuration = if (pathArguments.isEmpty()) {
            val manifest = projectRoot.resolve(EvoProjectManifest.FILE_NAME)
            require(manifest.isRegularFile()) {
                "No ${EvoProjectManifest.FILE_NAME} found in $projectRoot; pass Python files or a project directory"
            }
            EvoProjectManifest.parseConfiguration(Files.readString(manifest, StandardCharsets.UTF_8))
        } else {
            val files = collectPythonFiles(pathArguments.map { Paths.get(it).toAbsolutePath().normalize() })
            require(files.isNotEmpty()) { "No .py files were found" }
            if (files.any { !it.startsWith(projectRoot) }) {
                projectRoot = commonParent(files)
            }
            val entries = EvoProjectManifest.parse(
                EvoProjectManifest.render(files.map { projectRoot.relativize(it).toString().replace('\\', '/') }),
            ).map { it.copy(archived = targetOverride ?: it.archived) }
            EvoProjectManifest.Configuration(entries, alwaysPushAll = force)
        }

        val sources = configuration.entries.map { entry ->
            val path = EvoProjectManifest.resolveSource(projectRoot, entry.sourcePath)
            require(path.isRegularFile()) { "Declared source file does not exist: ${entry.sourcePath}" }
            entry.copy(archived = targetOverride ?: entry.archived) to Files.readString(path, StandardCharsets.UTF_8)
        }
        Terminal.info("CONNECT", "Looking for a TI-84 Evo over USB…")
        EvoSerialTransport.auto().use { transport ->
            transport.open()
            Terminal.success("Connected to ${transport.description}")
            val calculatorDirectory = EvoLink(transport).getDirectory()
            val pending = if (force || configuration.alwaysPushAll) {
                sources
            } else {
                EvoProjectUploadState.pending(projectRoot, sources, calculatorDirectory)
            }
            if (pending.isEmpty()) {
                Terminal.header("PROJECT IS CURRENT", projectRoot.toString())
                Terminal.success("Local sources and calculator programs are up to date — nothing to send.")
                return
            }

            Terminal.header("UPLOAD PLAN", projectRoot.toString())
            pending.forEachIndexed { planIndex, (entry, _) ->
                Terminal.plan(
                    planIndex + 1,
                    pending.size,
                    entry.sourcePath,
                    entry.programName,
                    if (entry.archived) "ARCHIVE" else "RAM",
                )
            }
            val skipped = sources.size - pending.size
            if (skipped > 0) Terminal.muted("  ↳ $skipped synchronized file(s) skipped")
            transport.close()
            transport.open()
            EvoPythonTransfer(transport).uploadProject(
                pending.map { (entry, source) ->
                    EvoPythonTransfer.Program(entry.programName, source, entry.archived)
                },
                onProgress = { result, completed, total ->
                    val (entry, source) = pending[completed - 1]
                    EvoProjectUploadState.markUploaded(projectRoot, entry, source)
                    Terminal.progress(
                        completed,
                        total,
                        result.programName,
                        if (result.archived) "ARCHIVE" else "RAM",
                        result.sourceBytes,
                    )
                },
            )
            Terminal.success("Upload complete — ${pending.size} file(s) synchronized.")
        }
    }

    private fun pull(arguments: List<String>) {
        var overwrite = false
        var projectRoot = Paths.get("").toAbsolutePath().normalize()
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "--force", "--overwrite" -> overwrite = true
                "--project" -> {
                    index++
                    require(index < arguments.size) { "--project requires a directory" }
                    projectRoot = Paths.get(arguments[index]).toAbsolutePath().normalize()
                }
                else -> error("Unknown pull option: $argument")
            }
            index++
        }
        Files.createDirectories(projectRoot)
        val manifestPath = projectRoot.resolve(EvoProjectManifest.FILE_NAME)
        val existing = if (manifestPath.isRegularFile()) {
            EvoProjectManifest.parseConfiguration(Files.readString(manifestPath, StandardCharsets.UTF_8))
        } else {
            null
        }

        Terminal.info("CONNECT", "Looking for a TI-84 Evo over USB…")
        val pulled = EvoSerialTransport.auto().use { transport ->
            transport.open()
            Terminal.success("Connected to ${transport.description}")
            EvoPythonProjectPuller(transport).pull { program, completed, total ->
                Terminal.progress(completed, total, program.programName, program.location, program.sourceBytes)
            }
        }
        val plan = EvoProjectPull.plan(projectRoot, pulled.programs, existing)
        Terminal.header("PULL PLAN", projectRoot.toString())
        plan.targets.forEachIndexed { planIndex, target ->
            Terminal.plan(
                planIndex + 1,
                plan.targets.size,
                target.entry.programName,
                target.entry.sourcePath,
                if (target.entry.archived) "ARCHIVE" else "RAM",
            )
        }
        if (plan.conflicts.isNotEmpty() && !overwrite) {
            error(
                "Pull would overwrite ${plan.conflicts.joinToString { it.entry.sourcePath }}; " +
                    "rerun with --force to replace local files",
            )
        }
        EvoProjectPull.write(plan, overwrite)
        Terminal.success(
            "Pulled ${plan.targets.size} Python file(s), wrote ${EvoProjectManifest.FILE_NAME}, " +
                "and synchronized local state.",
        )
    }

    private fun archiveFiles(arguments: List<String>) {
        require(arguments.isNotEmpty()) { "archive requires one or more NAME or NAME:TYPE selectors" }
        Terminal.info("CONNECT", "Looking for a TI-84 Evo over USB…")
        EvoSerialTransport.auto().use { transport ->
            transport.open()
            Terminal.success("Connected to ${transport.description}")
            val directory = EvoLink(transport).getDirectory()
            val selected = selectVariables(directory, arguments, "Archive")
            val alreadyArchived = selected.filter { it.archived }
            alreadyArchived.forEach { Terminal.muted("  ${it.name}:${it.type} is already in Archive") }
            val pending = selected.filterNot { it.archived }
            if (pending.isEmpty()) {
                Terminal.success("Every selected variable is already in Archive.")
                return
            }
            Terminal.header("ARCHIVE PLAN", "${pending.size} calculator variable(s)")
            pending.forEachIndexed { planIndex, entry ->
                Terminal.plan(planIndex + 1, pending.size, entry.name, entry.typeName, "ARCHIVE")
            }
            EvoVariableTransfer(transport).archiveVariables(pending) { result, completed, total ->
                Terminal.progress(
                    completed,
                    total,
                    result.entry.name,
                    "ARCHIVE",
                    result.payloadBytes,
                )
            }
            Terminal.success("Archived ${pending.size} calculator variable(s).")
        }
    }

    private fun deleteFiles(arguments: List<String>) {
        val assumeYes = arguments.any { it == "--yes" || it == "-y" }
        val selectors = arguments.filterNot { it == "--yes" || it == "-y" }
        require(selectors.isNotEmpty()) { "delete requires one or more NAME or NAME:TYPE selectors" }
        require(selectors.none { it.startsWith("-") }) { "Unknown delete option: ${selectors.first { it.startsWith("-") }}" }

        Terminal.info("CONNECT", "Looking for a TI-84 Evo over USB…")
        EvoSerialTransport.auto().use { transport ->
            transport.open()
            Terminal.success("Connected to ${transport.description}")
            val link = EvoLink(transport)
            val selected = selectVariables(link.getDirectory(), selectors, "Delete")

            Terminal.header("DELETE PLAN", "${selected.size} calculator variable(s)")
            selected.forEachIndexed { planIndex, entry ->
                val action = if (isPersistentBuiltInList(entry)) "CLEAR" else "DELETE"
                Terminal.plan(
                    planIndex + 1,
                    selected.size,
                    entry.name,
                    entry.typeName,
                    "$action ${entry.location.uppercase()}",
                )
            }
            if (!assumeYes && !Terminal.confirm("Apply this calculator file plan? L1–L6 values are cleared, not deleted.")) {
                Terminal.muted("  Operation cancelled; no calculator variables were changed.")
                return
            }

            val completed = try {
                link.deleteVariables(selected) { entry, completed, total ->
                    val action = if (isPersistentBuiltInList(entry)) "CLEARED" else "DELETED"
                    Terminal.progress(completed, total, entry.name, action, entry.size.toInt())
                }
            } catch (error: EvoVariableDeleteException) {
                if (error.deletedEntries.isNotEmpty()) {
                    Terminal.muted("  Already deleted: ${error.deletedEntries.joinToString { "${it.name}:${it.type}" }}")
                }
                if (error.clearedEntries.isNotEmpty()) {
                    Terminal.muted("  Already cleared: ${error.clearedEntries.joinToString { "${it.name}:${it.type}" }}")
                }
                throw error
            }
            val deleted = completed.count { !isPersistentBuiltInList(it) }
            val cleared = completed.size - deleted
            Terminal.success("Completed ${completed.size} operation(s): deleted $deleted, cleared $cleared.")
        }
    }

    private fun selectVariables(
        directory: List<EvoDirectoryEntry>,
        selectors: List<String>,
        action: String,
    ): List<EvoDirectoryEntry> = selectors.map { selector ->
        val separator = selector.lastIndexOf(':')
        val name = if (separator < 0) selector else selector.substring(0, separator)
        require(name.isNotBlank()) { "$action selector name cannot be empty: $selector" }
        val type = if (separator < 0) null else selector.substring(separator + 1).toIntOrNull()
            ?: error("$action selector type must be numeric: $selector")
        val matches = directory.filter {
            it.name.equals(name, ignoreCase = true) && (type == null || it.type == type)
        }
        require(matches.isNotEmpty()) { "No calculator variable matches $selector" }
        require(matches.size == 1) {
            "$selector is ambiguous; use NAME:TYPE (matching types: ${matches.joinToString { it.type.toString() }})"
        }
        matches.single()
    }.distinctBy { "${it.type}:${it.name.uppercase()}" }

    private fun collectPythonFiles(paths: List<Path>): List<Path> = paths.flatMap { path ->
        when {
            path.isRegularFile() && path.extension.equals("py", ignoreCase = true) -> listOf(path)
            path.isDirectory() -> Files.walk(path).use { stream ->
                stream.filter { it.isRegularFile() && it.extension.equals("py", ignoreCase = true) }
                    .filter { candidate ->
                        candidate.none { part -> part.toString().lowercase() in IGNORED_DIRECTORIES }
                    }
                    .toList()
            }
            else -> emptyList()
        }
    }.distinct().sortedBy(Path::toString)

    private fun commonParent(paths: List<Path>): Path {
        var parent = requireNotNull(paths.first().parent) {
            "Input paths must have a common filesystem root"
        }
        while (paths.any { !it.startsWith(parent) }) {
            parent = requireNotNull(parent.parent) {
                "Input paths must share a filesystem root"
            }
        }
        return parent
    }

    private fun installContextMenu() {
        require(System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "Explorer context-menu installation is only available on Windows"
        }
        val jar = codeJar()
        val launcher = jar.parent.resolve("ti84-evo.ps1")
        require(launcher.isRegularFile()) { "Keep ti84-evo.ps1 next to $jar before installing context menus" }
        val invocation =
            "powershell.exe -NoExit -ExecutionPolicy Bypass -File \"$launcher\" send \"%1\""
        addRegistryCommand("SystemFileAssociations\\.py\\shell\\Ti84EvoSend", invocation)
        addRegistryCommand("Directory\\shell\\Ti84EvoSend", invocation)
        Terminal.success("Installed “Send to TI-84 Evo” for Python files and folders.")
    }

    private fun uninstallContextMenu() {
        deleteRegistryKey("SystemFileAssociations\\.py\\shell\\Ti84EvoSend")
        deleteRegistryKey("Directory\\shell\\Ti84EvoSend")
        Terminal.success("Removed the TI-84 Evo Explorer context menus.")
    }

    private fun addRegistryCommand(relativeKey: String, invocation: String) {
        val base = "HKCU\\Software\\Classes\\$relativeKey"
        runRegistry("add", base, "/ve", "/d", "Send to TI-84 Evo", "/f")
        runRegistry("add", "$base\\command", "/ve", "/d", invocation, "/f")
    }

    private fun deleteRegistryKey(relativeKey: String) {
        runRegistry("delete", "HKCU\\Software\\Classes\\$relativeKey", "/f", allowMissing = true)
    }

    private fun runRegistry(vararg args: String, allowMissing: Boolean = false) {
        val exit = ProcessBuilder(listOf("reg.exe") + args).inheritIO().start().waitFor()
        if (exit != 0 && !allowMissing) error("reg.exe failed with exit code $exit")
    }

    private fun codeJar(): Path {
        val location = Path.of(EvoCli::class.java.protectionDomain.codeSource.location.toURI())
        require(location.isRegularFile() && location.extension.equals("jar", ignoreCase = true)) {
            "Context menus must be installed from the packaged ti84-evo-cli.jar"
        }
        return location.toAbsolutePath()
    }

    private fun usage() {
        Terminal.header("TI-84 EVO SENDER", "PowerShell • PyCharm • Explorer")
        println(
            """
            TI-84 Evo sender

              ti84-evo send [--project DIR] [--all|--always-rebuild] [--archive|--ram] [FILE|DIR ...]
              ti84-evo pull [--project DIR] [--force]
              ti84-evo archive NAME[:TYPE] [NAME[:TYPE] ...]
              ti84-evo delete [--yes] NAME[:TYPE] [NAME[:TYPE] ...]
              ti84-evo list-files
              ti84-evo install-context-menu
              ti84-evo uninstall-context-menu

            With no paths, send reads .ti84-evo-project. Normal sends upload only changed files.
            """.trimIndent(),
        )
    }

    private val IGNORED_DIRECTORIES = setOf(
        ".git", ".idea", ".venv", "venv", "__pycache__", "build", "dist", "node_modules",
    )

    private fun formatBytes(bytes: Long): String = "$bytes B"

    private object Terminal {
        private val color = System.getenv("NO_COLOR") == null &&
            (System.console() != null || System.getenv("WT_SESSION") != null || System.getenv("TERM") != null)
        private const val RESET = "\u001B[0m"
        private const val BOLD = "\u001B[1m"
        private const val DIM = "\u001B[2m"
        private const val CYAN = "\u001B[96m"
        private const val BLUE = "\u001B[94m"
        private const val GREEN = "\u001B[92m"
        private const val YELLOW = "\u001B[93m"
        private const val RED = "\u001B[91m"
        private const val MAGENTA = "\u001B[95m"
        private const val BOX_WIDTH = 61

        fun header(title: String, subtitle: String) {
            val brand = "  ◈ TI-84 EVO  "
            println()
            println(paint("╭" + "─".repeat(BOX_WIDTH) + "╮", CYAN))
            println(
                paint("│", CYAN) + paint(brand, BOLD, CYAN) +
                    paint(title.take(BOX_WIDTH - brand.length).padEnd(BOX_WIDTH - brand.length), BOLD) +
                    paint("│", CYAN),
            )
            println(
                paint("│  ", CYAN) + paint(subtitle.take(BOX_WIDTH - 2).padEnd(BOX_WIDTH - 2), DIM) +
                    paint("│", CYAN),
            )
            println(paint("╰" + "─".repeat(BOX_WIDTH) + "╯", CYAN))
        }

        fun plan(index: Int, total: Int, source: String, name: String, target: String) {
            val counter = "${index.toString().padStart(total.toString().length)}/$total"
            println(
                "  " + paint(counter, DIM) + "  " + paint(source.padEnd(30), BLUE) +
                    "  →  " + paint(name.padEnd(8), BOLD) + "  " + paint(target, MAGENTA),
            )
        }

        fun progress(completed: Int, total: Int, name: String, target: String, bytes: Int) {
            val filled = ((completed.toDouble() / total) * 20).toInt().coerceIn(0, 20)
            val bar = "━".repeat(filled) + "─".repeat(20 - filled)
            val percent = completed * 100 / total
            println(
                "  " + paint(bar, GREEN) + "  " + paint("${percent.toString().padStart(3)}%", BOLD, GREEN) +
                    "  ${name.padEnd(8)}  " + paint(target.padEnd(7), MAGENTA) + "  " + paint("$bytes B", DIM),
            )
        }

        fun info(label: String, message: String) =
            println("\n  " + paint("◆ $label", BOLD, YELLOW) + "  $message")

        fun confirm(message: String): Boolean {
            checkNotNull(System.console()) {
                "Deletion requires an interactive console; rerun with --yes to confirm"
            }
            print("  " + paint("◆ CONFIRM", BOLD, RED) + "  $message [y/N] ")
            System.out.flush()
            val answer = readlnOrNull()?.trim().orEmpty()
            return answer.equals("y", ignoreCase = true) || answer.equals("yes", ignoreCase = true)
        }

        fun success(message: String) = println("\n  " + paint("✓", BOLD, GREEN) + "  " + paint(message, BOLD))

        fun muted(message: String) = println(paint(message, DIM))

        fun error(message: String) {
            System.err.println()
            System.err.println(paint("  ✕  TI-84 EVO ERROR", BOLD, RED))
            System.err.println("     $message")
            if (
                "no ti-84 evo" in message.lowercase() ||
                "failed to open" in message.lowercase() ||
                "timed out" in message.lowercase()
            ) {
                System.err.println(
                    paint(
                        "     Check USB, wake the calculator, and close other calculator-link software.",
                        YELLOW,
                    ),
                )
            }
            System.err.println()
        }

        private fun paint(text: String, vararg codes: String): String =
            if (color) codes.joinToString("") + text + RESET else text
    }
}
