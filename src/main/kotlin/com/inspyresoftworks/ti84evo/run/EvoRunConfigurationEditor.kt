package com.inspyresoftworks.ti84evo.run

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class EvoRunConfigurationEditor(private val project: Project) : SettingsEditor<EvoRunConfiguration>() {
    private val manifestField = JBTextField(EvoProjectManifest.FILE_NAME)
    private val programCombo = JComboBox<String>().apply { isEditable = true }
    private val liveScreenCheckBox = JBCheckBox("Open live screen viewer after launch")
    private val panel = JPanel(BorderLayout()).apply {
        add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Project manifest:"), manifestField, 1, false)
                .addLabeledComponent(JBLabel("Launch program:"), programCombo, 1, false)
                .addComponent(liveScreenCheckBox, 1)
                .addComponentFillVertically(JPanel(), 0)
                .panel,
            BorderLayout.CENTER,
        )
    }

    override fun resetEditorFrom(configuration: EvoRunConfiguration) {
        manifestField.text = configuration.manifestPath
        val names = runCatching {
            configuration.resolveProject().programs.map { it.entry.programName }
        }.getOrDefault(emptyList())
        programCombo.model = DefaultComboBoxModel(names.toTypedArray())
        programCombo.selectedItem = configuration.programName
        liveScreenCheckBox.isSelected = configuration.openLiveScreenViewer
    }

    override fun applyEditorTo(configuration: EvoRunConfiguration) {
        configuration.manifestPath = manifestField.text.trim().ifBlank { EvoProjectManifest.FILE_NAME }
        configuration.programName = programCombo.editor.item?.toString()?.trim()?.uppercase().orEmpty()
        configuration.openLiveScreenViewer = liveScreenCheckBox.isSelected
    }

    override fun createEditor(): JComponent = panel
}
