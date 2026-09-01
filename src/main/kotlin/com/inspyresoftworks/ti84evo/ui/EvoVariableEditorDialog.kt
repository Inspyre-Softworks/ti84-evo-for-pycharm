package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.protocol.EvoVariablePayload
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/** Creates or replaces numbers, lists, and matrices through the Evo ASCII importer. */
internal class EvoVariableEditorDialog(
    private val project: Project,
    initialKind: EvoVariablePayload.Kind = EvoVariablePayload.Kind.NUMBER,
    initialName: String = "A",
    initialArchived: Boolean = false,
    initialValue: String = "",
    replacing: Boolean = false,
) : DialogWrapper(project) {
    private val kind = JComboBox(EvoVariablePayload.Kind.entries.toTypedArray()).apply {
        selectedItem = initialKind
        renderer = javax.swing.DefaultListCellRenderer().also { renderer ->
            setRenderer { list, value, index, selected, focus ->
                renderer.getListCellRendererComponent(list, value?.wireName, index, selected, focus)
            }
        }
    }
    private val name = JBTextField(initialName, 12)
    private val value = JBTextArea(8, 48).apply {
        text = initialValue
        lineWrap = true
        wrapStyleWord = true
    }
    private val archive = JCheckBox("Store in Archive", initialArchived)
    private val hint = JBLabel()
    private var previousKind = initialKind

    lateinit var editedValue: EvoVariablePayload.EditableValue
        private set

    init {
        title = if (replacing) "Replace Calculator Variable" else "Add Calculator Variable"
        setOKButtonText("Send to Calculator")
        kind.addActionListener {
            val selectedKind = kind.selectedItem as EvoVariablePayload.Kind
            if (name.text.trim().equals(defaultName(previousKind), ignoreCase = true)) {
                name.text = defaultName(selectedKind)
            }
            previousKind = selectedKind
            updateHint()
        }
        updateHint()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val fields = JPanel(GridBagLayout()).apply {
            add(JBLabel("Type:"), constraints(0, 0))
            add(kind, constraints(1, 0, fill = GridBagConstraints.HORIZONTAL, weight = 1.0))
            add(JBLabel("Name:"), constraints(0, 1))
            add(this@EvoVariableEditorDialog.name, constraints(1, 1, fill = GridBagConstraints.HORIZONTAL, weight = 1.0))
            add(hint, constraints(0, 2, width = 2, fill = GridBagConstraints.HORIZONTAL, weight = 1.0))
            add(archive, constraints(0, 3, width = 2))
        }
        return JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(10)
            add(fields, BorderLayout.NORTH)
            add(JBScrollPane(value), BorderLayout.CENTER)
        }
    }

    override fun doOKAction() {
        val candidate = EvoVariablePayload.EditableValue(
            kind.selectedItem as EvoVariablePayload.Kind,
            name.text,
            value.text,
            archive.isSelected,
        )
        runCatching {
            EvoVariablePayload.build(candidate)
            candidate.copy(
                name = EvoVariablePayload.normalizeName(candidate.kind, candidate.name),
                value = EvoVariablePayload.normalizeValue(candidate.kind, candidate.value),
            )
        }.onSuccess {
            editedValue = it
            super.doOKAction()
        }.onFailure {
            Messages.showErrorDialog(project, it.message ?: "Invalid calculator value", title)
        }
    }

    private fun updateHint() {
        hint.text = when (kind.selectedItem as EvoVariablePayload.Kind) {
            EvoVariablePayload.Kind.NUMBER -> "Enter a real, fraction, or complex value; name must be A–Z."
            EvoVariablePayload.Kind.LIST ->
                "Use L1–L6 for a Stats editor column. Custom names are available from the calculator's LIST menu."
            EvoVariablePayload.Kind.MATRIX -> "Enter one comma/space-separated numeric row per line; name must be A–J."
        }
    }

    private fun defaultName(kind: EvoVariablePayload.Kind): String = when (kind) {
        EvoVariablePayload.Kind.NUMBER, EvoVariablePayload.Kind.MATRIX -> "A"
        EvoVariablePayload.Kind.LIST -> "L1"
    }

    private fun constraints(x: Int, y: Int, width: Int = 1, fill: Int = GridBagConstraints.NONE, weight: Double = 0.0) =
        GridBagConstraints().apply {
            gridx = x; gridy = y; gridwidth = width; this.fill = fill; weightx = weight
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 4, 4, 12)
        }
}
