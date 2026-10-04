package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.protocol.EvoLink
import com.inspyresoftworks.ti84evo.transport.EvoSerialTransport
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.util.WeakHashMap
import javax.swing.Action
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/** Modeless, read-only calculator framebuffer viewer used by run configurations. */
internal class EvoLiveScreenDialog private constructor(private val ownerProject: Project) : DialogWrapper(ownerProject) {
    private val screen = JBLabel("Waiting for the first frame", SwingConstants.CENTER).apply {
        preferredSize = Dimension(640, 480)
    }
    private val status = JBLabel("Connecting to the calculator...")

    @Volatile
    private var running = true

    @Volatile
    private var transport: EvoSerialTransport? = null

    init {
        title = "TI-84 Evo Live Screen"
        isModal = false
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(10)
        add(screen, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }

    override fun doOKAction() {
        stopCapture()
        super.doOKAction()
    }

    override fun doCancelAction() {
        stopCapture()
        super.doCancelAction()
    }

    override fun dispose() {
        stopCapture()
        super.dispose()
    }

    private fun startCapture() {
        Thread({
            try {
                EvoSerialTransport.auto().use { opened ->
                    transport = opened
                    opened.open()
                    val link = EvoLink(opened)
                    updateStatus("Connected; close this window to stop live capture.")
                    while (running && !ownerProject.isDisposed) {
                        val capture = link.getScreenCapture()
                        val image = EvoImage.toBufferedImage(capture)
                        val scaled = image.getScaledInstance(640, 480, Image.SCALE_FAST)
                        ApplicationManager.getApplication().invokeLater {
                            if (running && isShowing) {
                                screen.icon = ImageIcon(scaled)
                                screen.text = null
                                status.text = "Live ${capture.width}x${capture.height}x${capture.bitsPerPixel}"
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                if (running) {
                    updateStatus("Live screen stopped: ${error.message ?: error.javaClass.simpleName}")
                }
            } finally {
                transport = null
            }
        }, "TI-84 Evo live screen").apply { isDaemon = true }.start()
    }

    private fun updateStatus(message: String) {
        ApplicationManager.getApplication().invokeLater {
            if (running && isShowing) status.text = message
        }
    }

    private fun stopCapture() {
        running = false
        runCatching { transport?.close() }
        remove(ownerProject, this)
    }

    companion object {
        private val dialogs = WeakHashMap<Project, EvoLiveScreenDialog>()

        fun open(project: Project) {
            close(project)
            val dialog = EvoLiveScreenDialog(project)
            dialogs[project] = dialog
            dialog.show()
            dialog.startCapture()
        }

        fun close(project: Project): Boolean {
            val dialog = dialogs.remove(project) ?: return false
            dialog.stopCapture()
            if (dialog.isShowing) dialog.close(CANCEL_EXIT_CODE)
            return true
        }

        private fun remove(project: Project, dialog: EvoLiveScreenDialog) {
            if (dialogs[project] === dialog) dialogs.remove(project)
        }
    }
}
