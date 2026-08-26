package com.inspyresoftworks.ti84evo.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JCheckBox
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.AbstractTableModel

/** Complete editor for the project manifest. */
internal class EvoProjectConfigurationDialog(
    private val project: Project,
    private val projectRoot: Path,
    initialEntries: List<EvoProjectManifest.Entry>,
    initialAlwaysPushAll: Boolean,
) : DialogWrapper(project, true) {
    private data class Row(var sourcePath: String, var programName: String, var archived: Boolean)

    private val rows = initialEntries.mapTo(mutableListOf()) { Row(it.sourcePath, it.programName, it.archived) }
    private val model = object : AbstractTableModel() {
        private val columns = arrayOf("Project file", "Calculator name", "Archive")

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == 2) Boolean::class.javaObjectType else String::class.java
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex != 0
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
            0 -> rows[rowIndex].sourcePath
            1 -> rows[rowIndex].programName
            else -> rows[rowIndex].archived
        }
        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            when (columnIndex) {
                1 -> rows[rowIndex].programName = value?.toString()?.trim()?.uppercase().orEmpty()
                2 -> rows[rowIndex].archived = value as? Boolean ?: false
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }

        fun changed() = fireTableDataChanged()
    }
    private val table = JBTable(model).apply {
        selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        fillsViewportHeight = true
        preferredScrollableViewportSize = Dimension(680, 300)
        columnModel.getColumn(0).preferredWidth = 400
        columnModel.getColumn(1).preferredWidth = 160
        columnModel.getColumn(2).preferredWidth = 80
        emptyText.text = "Add Python files to this calculator project"
    }
    private val alwaysPushAll = JCheckBox(
        "Always rebuild / push all files",
        initialAlwaysPushAll,
    ).apply {
        toolTipText = "Useful when the calculator is frequently reset or the project is sent to different calculators"
    }

    var entries: List<EvoProjectManifest.Entry> = emptyList()
        private set
    val shouldAlwaysPushAll: Boolean
        get() = alwaysPushAll.isSelected

    init {
        title = "Configure TI-84 Evo Project"
        setOKButtonText("Save Configuration")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val decorated = ToolbarDecorator.createDecorator(table)
            .setAddAction { addFiles() }
            .setRemoveAction { removeSelected() }
            .setMoveUpAction { moveSelected(-1) }
            .setMoveDownAction { moveSelected(1) }
            .setAddIcon(AllIcons.General.Add)
            .createPanel()
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(decorated, BorderLayout.CENTER)
            add(alwaysPushAll, BorderLayout.SOUTH)
        }
    }

    override fun doOKAction() {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        val candidate = rows.map {
            EvoProjectManifest.Entry(it.sourcePath, it.programName.uppercase(), it.archived)
        }
        runCatching { EvoProjectManifest.renderEntries(candidate, alwaysPushAll.isSelected) }
            .onSuccess {
                entries = candidate
                super.doOKAction()
            }
            .onFailure {
                Messages.showErrorDialog(project, it.message ?: "Invalid project configuration", title)
            }
    }

    private fun addFiles() {
        val chooser = JFileChooser(projectRoot.toFile()).apply {
            dialogTitle = "Add Python Project Files"
            isMultiSelectionEnabled = true
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Python source files (*.py)", "py")
        }
        if (chooser.showOpenDialog(contentPanel) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFiles.takeIf { it.isNotEmpty() }
            ?: chooser.selectedFile?.let { arrayOf(it) }
            ?: emptyArray()
        val existingPaths = rows.mapTo(mutableSetOf()) { it.sourcePath.lowercase() }
        val usedNames = rows.mapTo(mutableSetOf()) { it.programName.uppercase() }
        for (file in selected) {
            val path = file.toPath().toAbsolutePath().normalize()
            if (!path.startsWith(projectRoot)) {
                Messages.showErrorDialog(
                    project,
                    "Project files must be inside $projectRoot:\n$path",
                    title,
                )
                continue
            }
            val relative = projectRoot.relativize(path).toString().replace('\\', '/')
            if (!existingPaths.add(relative.lowercase())) continue
            val generated = EvoProjectManifest.parse(
                EvoProjectManifest.render(listOf(relative)),
            ).single().programName
            var name = generated
            var suffix = 2
            while (!usedNames.add(name)) {
                val suffixText = suffix++.toString()
                name = generated.take(8 - suffixText.length) + suffixText
            }
            rows += Row(relative, name, false)
        }
        model.changed()
    }

    private fun removeSelected() {
        table.selectedRows.sortedDescending().forEach(rows::removeAt)
        model.changed()
    }

    private fun moveSelected(delta: Int) {
        val row = table.selectedRow
        if (row < 0) return
        val target = row + delta
        if (target !in rows.indices) return
        val value = rows.removeAt(row)
        rows.add(target, value)
        model.changed()
        table.setRowSelectionInterval(target, target)
    }
}
