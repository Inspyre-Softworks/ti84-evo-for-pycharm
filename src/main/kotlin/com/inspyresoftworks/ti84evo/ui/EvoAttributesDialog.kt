package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class EvoAttributesDialog(
    project: Project,
    attributes: Map<String, Any?>,
) : DialogWrapper(project) {
    private val rows = EvoAttributePresentation.rows(attributes)
    private val markdown = EvoAttributePresentation.toMarkdown(attributes)
    private val copyAction = object : AbstractAction("Copy Attributes") {
        override fun actionPerformed(event: ActionEvent?) {
            CopyPasteManager.getInstance().setContents(StringSelection(markdown))
            putValue(NAME, "Copied!")
            isEnabled = false
        }
    }

    init {
        title = "TI-84 Evo Attributes"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4, 8, 8, 8)
        }
        rows.groupBy { it.section }.forEach { (section, sectionRows) ->
            content.add(TitledSeparator(section))
            content.add(sectionPanel(sectionRows))
        }
        return JBScrollPane(content).apply {
            preferredSize = JBUI.size(560, 430)
            border = JBUI.Borders.empty()
        }
    }

    override fun createActions(): Array<Action> = arrayOf(copyAction, okAction)

    private fun sectionPanel(sectionRows: List<EvoAttributeRow>): JPanel = JPanel(GridBagLayout()).apply {
        border = JBUI.Borders.empty(4, 10, 10, 10)
        sectionRows.forEachIndexed { index, row ->
            add(
                JBLabel(row.label),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = index
                    anchor = GridBagConstraints.NORTHWEST
                    insets = Insets(3, 0, 3, 20)
                },
            )
            add(
                JBLabel(row.value).apply { toolTipText = "Protocol key: ${row.key}" },
                GridBagConstraints().apply {
                    gridx = 1
                    gridy = index
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    anchor = GridBagConstraints.NORTHWEST
                    insets = Insets(3, 0, 3, 0)
                },
            )
        }
        add(
            JPanel(BorderLayout()).apply { isOpaque = false },
            GridBagConstraints().apply {
                gridx = 0
                gridy = sectionRows.size
                gridwidth = 2
                weighty = 1.0
                fill = GridBagConstraints.VERTICAL
            },
        )
    }
}
