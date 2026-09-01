package com.inspyresoftworks.ti84evo.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class EvoAboutDialog(
    project: Project,
    private val version: String,
    private val pluginId: String,
) : DialogWrapper(project) {
    private val project = project

    init {
        title = "About TI-84 Evo"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 12)).apply {
        border = JBUI.Borders.empty(12)
        add(
            JBLabel(
                "<html><h2>TI-84 Evo for PyCharm</h2>" +
                    "Version: $version<br>" +
                    "Build: ${if (version.endsWith("-SNAPSHOT")) "Development snapshot" else "Release"}<br>" +
                    "Plugin ID: $pluginId</html>",
            ),
            BorderLayout.CENTER,
        )
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(JButton("Project GitHub").apply { addActionListener { BrowserUtil.browse(GITHUB_URL) } })
                add(JButton("Read the Docs").apply { addActionListener { BrowserUtil.browse(DOCS_URL) } })
                add(JButton("View License").apply { addActionListener { showLicense() } })
            },
            BorderLayout.SOUTH,
        )
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
        const val DOCS_URL = "https://ti84-evo-for-pycharm.readthedocs.io/en/latest/"
    }
}
