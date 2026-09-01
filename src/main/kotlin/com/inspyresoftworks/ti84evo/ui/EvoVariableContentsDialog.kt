package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.protocol.EvoVariableFile
import com.inspyresoftworks.ti84evo.protocol.EvoVariableDecoder
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.nio.file.Files
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JFileChooser

/** Raw, lossless variable viewer with export and supported-value replacement actions. */
internal class EvoVariableContentsDialog(
    private val project: Project,
    private val entry: EvoDirectoryEntry,
    private val raw: ByteArray,
) : DialogWrapper(project) {
    private val inspection = EvoVariableFile.inspect(raw)
    private val decodedValue = runCatching {
        EvoVariableDecoder.decode(raw, entry.type, entry.name, entry.archived)
    }
    private val text = buildString {
        appendLine("Name: ${entry.name}")
        appendLine("Type: ${entry.typeName} (${entry.type})")
        appendLine("Location: ${entry.location}")
        appendLine("Transfer envelope: ${inspection.rawBytes} bytes")
        appendLine("Variable data: ${inspection.data.size} bytes")
        inspection.metadata.forEach { (key, value) ->
            appendLine("$key: ${if (value is ByteArray) "bytes[${value.size}]" else value}")
        }
        decodedValue.onSuccess { editable ->
            appendLine()
            appendLine("Decoded value")
            appendLine(editable.value.ifEmpty { "(empty list)" })
        }.onFailure { error ->
            if (entry.type in EDITABLE_TYPES) {
                appendLine()
                appendLine("This value could not be decoded for editing: ${error.message}")
            }
        }
        appendLine()
        appendLine("Raw variable data (hex)")
        val preview = inspection.data.take(HEX_PREVIEW_BYTES)
        preview.forEachIndexed { index, byte ->
            if (index > 0 && index % 16 == 0) appendLine()
            append("%02X ".format(byte.toInt() and 0xFF))
        }
        if (inspection.data.size > preview.size) {
            appendLine()
            append("… ${inspection.data.size - preview.size} more bytes; use Save Copy for the complete variable.")
        }
    }.trimEnd()
    private val copyAction = object : AbstractAction("Copy") {
        override fun actionPerformed(event: ActionEvent?) {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
        }
    }
    private val exportAction = object : AbstractAction("Save Copy…") {
        override fun actionPerformed(event: ActionEvent?) = export()
    }
    private val replaceAction = object : AbstractAction("Replace Value…") {
        override fun actionPerformed(event: ActionEvent?) = close(REPLACE_EXIT_CODE)
    }.apply { isEnabled = decodedValue.isSuccess }

    val shouldReplace: Boolean get() = exitCode == REPLACE_EXIT_CODE
    val editableValue get() = decodedValue.getOrNull()

    init {
        title = "${entry.name} — ${entry.typeName}"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent = JBScrollPane(JBTextArea(text).apply {
        isEditable = false
        caretPosition = 0
    }).apply {
        preferredSize = JBUI.size(650, 430)
    }

    override fun createActions(): Array<Action> = arrayOf(replaceAction, exportAction, copyAction, okAction)

    private fun export() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save Calculator Variable"
            selectedFile = java.io.File("${entry.name}.${extension(entry.type)}")
        }
        if (chooser.showSaveDialog(contentPanel) != JFileChooser.APPROVE_OPTION) return
        runCatching { Files.write(chooser.selectedFile.toPath(), EvoVariableFile.addChecksum(raw)) }
            .onFailure { Messages.showErrorDialog(project, it.message ?: "Could not save the variable", title) }
    }

    companion object {
        const val REPLACE_EXIT_CODE = NEXT_USER_EXIT_CODE
        const val HEX_PREVIEW_BYTES = 8 * 1024
        private val EDITABLE_TYPES = setOf(0, 1, 6)

        private fun extension(type: Int): String = when (type) {
            0 -> "8xn2"; 1 -> "8xl2"; 2 -> "8xp2"; 3 -> "8xd2"; 4 -> "8ci2"
            5 -> "8ca2"; 6 -> "8xm2"; 7 -> "8xy2"; 8 -> "8xv2"; 10 -> "8xs2"
            12 -> "8xw2"; 13 -> "8xz2"; 14 -> "8xt2"; 15 -> "8xpy2"; else -> "bin"
        }
    }
}
