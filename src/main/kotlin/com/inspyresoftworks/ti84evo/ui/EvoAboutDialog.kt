package com.inspyresoftworks.ti84evo.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class EvoAboutDialog(
    project: Project,
    versionStatus: EvoVersionStatus,
    private val pluginId: String,
    private val mischiefMode: Boolean,
    private val retryMarketplaceValidation: () -> Unit,
    private val debugInfo: (EvoVersionStatus) -> String,
    private val openInstalledPluginDirectory: () -> Unit,
) : DialogWrapper(project) {
    private val project = project
    private var versionStatus = versionStatus
    private val versionDetails = JBLabel()
    private val retryButton = JButton("Retry Marketplace Validation").apply {
        addActionListener { retryMarketplaceValidation() }
    }

    init {
        title = "About TI-84 Evo"
        setOKButtonText("Close")
        init()
        updateVersionStatus(versionStatus)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 12)).apply {
        border = JBUI.Borders.empty(12)
        add(
            versionDetails,
            BorderLayout.CENTER,
        )
        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(buttonRow(
                JButton("Project GitHub").apply { addActionListener { BrowserUtil.browse(GITHUB_URL) } },
                JButton("Read the Docs").apply { addActionListener { BrowserUtil.browse(DOCS_URL) } },
                JButton("View License").apply { addActionListener { showLicense() } },
                JButton("Copy Debug Info").apply { addActionListener { copyDebugInfo() } },
            ))
            add(buttonRow(retryButton))
            if (mischiefMode) {
                add(buttonRow(
                    JButton("Submit Issue/Bug").apply { addActionListener { BrowserUtil.browse(ISSUES_URL) } },
                    JButton("Open Installed Plugin Directory").apply {
                        addActionListener { openInstalledPluginDirectory() }
                    },
                ))
            }
        }, BorderLayout.SOUTH)
    }

    internal fun updateVersionStatus(status: EvoVersionStatus) {
        versionStatus = status
        retryButton.isVisible = status.isUnverified
        versionDetails.text = "<html><h2>TI-84 Evo for PyCharm</h2>" +
            "Version: ${status.displayVersion}<br>" +
            "Build: ${status.buildDescription}<br>" +
            "Marketplace: ${status.marketplaceDescription}<br>" +
            "Verification: ${status.detail}<br>" +
            "Plugin ID: $pluginId</html>"
    }

    private fun buttonRow(vararg buttons: JButton): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
        buttons.forEach(::add)
    }

    private fun copyDebugInfo() {
        CopyPasteManager.getInstance().setContents(StringSelection(debugInfo(versionStatus)))
    }

    private fun showLicense() {
        val text = javaClass.getResourceAsStream("/META-INF/ti84-evo-license.txt")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: "No license information was bundled with this build."
        Messages.showMessageDialog(project, text, "TI-84 Evo License", Messages.getInformationIcon())
    }

    companion object {
        const val GITHUB_URL = "https://github.com/Inspyre-Softworks/ti84-evo-for-pycharm"
        const val ISSUES_URL = "$GITHUB_URL/issues/new"
        const val DOCS_URL = "https://ti84-evo-for-pycharm.readthedocs.io/en/latest/"
    }
}
