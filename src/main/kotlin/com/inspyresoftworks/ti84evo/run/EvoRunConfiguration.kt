package com.inspyresoftworks.ti84evo.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.options.SettingsEditor
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import com.inspyresoftworks.ti84evo.project.EvoProjectResolver
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class EvoRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<EvoRunConfigurationOptions>(project, factory, name) {
    override fun getOptions(): EvoRunConfigurationOptions = super.getOptions() as EvoRunConfigurationOptions

    var manifestPath: String
        get() = options.manifestPath ?: EvoProjectManifest.FILE_NAME
        set(value) { options.manifestPath = value }

    var programName: String
        get() = options.programName.orEmpty()
        set(value) { options.programName = value }

    var openLiveScreenViewer: Boolean
        get() = options.openLiveScreenViewer
        set(value) { options.openLiveScreenViewer = value }

    override fun getConfigurationEditor(): SettingsEditor<out EvoRunConfiguration> = EvoRunConfigurationEditor(project)

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        val resolved = runCatching { resolveProject() }.getOrElse {
            throw RuntimeConfigurationError(it.message ?: "Could not read the Evo project manifest")
        }
        val target = resolved.programs.singleOrNull {
            it.entry.programName.equals(programName.trim(), ignoreCase = true)
        } ?: throw RuntimeConfigurationError("Select a launch program declared by ${EvoProjectManifest.FILE_NAME}")
        if (target.entry.archived) throw RuntimeConfigurationError("Launch program ${target.entry.programName} must use RAM")
        if (target.entry.programName.firstOrNull() !in 'A'..'Z') {
            throw RuntimeConfigurationError("Launch program names must start with a letter")
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment) =
        EvoRunProfileState(environment, this)

    internal fun resolveProject() = ApplicationManager.getApplication().runReadAction<com.inspyresoftworks.ti84evo.project.EvoResolvedProject> {
        EvoProjectResolver.resolve(resolveManifestPath()) { path -> readCurrentText(path) }
    }

    internal fun resolveManifestPath(): Path {
        val base = project.basePath?.let(Path::of)
            ?: throw IllegalStateException("Open a project directory first")
        return base.resolve(manifestPath.ifBlank { EvoProjectManifest.FILE_NAME }).normalize()
    }

    private fun readCurrentText(path: Path): String? {
        val file = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return null
        if (file.isDirectory) return null
        return FileDocumentManager.getInstance().getDocument(file)?.text
            ?: String(file.contentsToByteArray(), StandardCharsets.UTF_8)
    }
}
