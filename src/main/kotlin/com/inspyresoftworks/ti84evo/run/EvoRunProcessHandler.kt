package com.inspyresoftworks.ti84evo.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.inspyresoftworks.ti84evo.project.EvoProjectRunner
import com.inspyresoftworks.ti84evo.transport.EvoSerialTransport
import com.inspyresoftworks.ti84evo.ui.EvoLiveScreenDialog
import java.io.OutputStream
import java.util.concurrent.CancellationException

class EvoRunProcessHandler(private val configuration: EvoRunConfiguration) : ProcessHandler() {
    @Volatile
    private var cancelled = false

    fun start() {
        startNotify()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                var closedViewer = false
                ApplicationManager.getApplication().invokeAndWait {
                    closedViewer = EvoLiveScreenDialog.close(configuration.project)
                }
                if (closedViewer) text("Closed the previous live screen viewer before reconnecting.\n")
                text("Connecting to a TI-84 Evo over USB…\n")
                val project = configuration.resolveProject()
                val result = EvoSerialTransport.auto().use { transport ->
                    transport.open()
                    text("Connected to ${transport.description}\n")
                    EvoProjectRunner(transport).run(
                        project = project,
                        launchProgram = configuration.programName,
                        onProgress = { text("$it\n") },
                        isCancelled = { cancelled },
                    )
                }
                text(
                    "Launched ${result.launch.programName}; uploaded ${result.uploadedPrograms.size}, " +
                        "skipped ${result.skippedPrograms}.\n",
                )
                if (configuration.openLiveScreenViewer) {
                    text("Opening the live calculator screen viewer.\n")
                    ApplicationManager.getApplication().invokeLater {
                        if (!configuration.project.isDisposed) {
                            EvoLiveScreenDialog.open(configuration.project)
                        }
                    }
                }
                notifyProcessTerminated(0)
            } catch (_: CancellationException) {
                text("TI-84 Evo run cancelled.\n", error = true)
                notifyProcessTerminated(130)
            } catch (error: Throwable) {
                text("TI-84 Evo run failed: ${error.message ?: error.javaClass.simpleName}\n", error = true)
                notifyProcessTerminated(1)
            }
        }
    }

    override fun destroyProcessImpl() {
        cancelled = true
    }

    override fun detachProcessImpl() {
        cancelled = true
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null

    private fun text(message: String, error: Boolean = false) {
        notifyTextAvailable(message, if (error) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT)
    }
}
