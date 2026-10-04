package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.JBScrollPane
import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.charset.StandardCharsets
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

internal class EvoChangelogDialog(
    project: Project,
    private val html: String,
    private val mischiefMode: Boolean,
    private val settings: EvoApplicationSettings,
) : DialogWrapper(project) {
    init {
        title = "What's New in TI-84 Evo ${EvoBuildInfo.version}"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        add(JBScrollPane(JEditorPane("text/html", html).apply {
            isEditable = false
            isOpaque = false
            caretPosition = 0
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.url != null) {
                    BrowserUtil.browse(event.url)
                }
            }
        }).apply {
            preferredSize = Dimension(640, 340)
        }, BorderLayout.CENTER)
        if (mischiefMode) {
            add(JCheckBox("Always Show Changelog", settings.snapshot(true).alwaysShowChangelog).apply {
                addActionListener { settings.setAlwaysShowChangelog(isSelected) }
            }, BorderLayout.SOUTH)
        }
    }
}

internal object EvoChangelog {
    private val tagOrder = listOf("Feature - Production", "Feature - Dev", "Bugfix", "Enhance", "Code Clean", "Docs")
    private val taggedNote = Regex("^\\[(Feature - Production|Feature - Dev|Bugfix|Enhance|Code Clean|Docs)] (.+)$")

    fun currentVersionHtml(): String? {
        val changelog = javaClass.getResourceAsStream("/META-INF/ti84-evo-changelog.md")
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
            ?: return null
        return htmlForVersion(changelog, EvoBuildInfo.version)
    }

    internal fun htmlForVersion(changelog: String, version: String): String? {
        val lines = changelog.lineSequence().toList()
        val start = lines.indexOf("## $version")
        if (start < 0) return null
        val section = lines.drop(start + 1).takeWhile { !it.startsWith("## ") }
        val bullets = mutableListOf<StringBuilder>()
        for (line in section) {
            when {
                line.startsWith("- ") -> bullets.add(StringBuilder(line.removePrefix("- ")))
                line.startsWith("  ") && bullets.isNotEmpty() -> bullets.last().append(' ').append(line.trim())
            }
        }
        if (bullets.isEmpty()) return null
        val grouped = bullets.groupBy { taggedNote.matchEntire(it.toString())?.groupValues?.get(1) ?: "Other" }
        val sections = (tagOrder + "Other").joinToString("") { tag ->
            val notes = grouped[tag].orEmpty()
            if (notes.isEmpty()) "" else {
                val items = notes.joinToString("") { note ->
                    val body = taggedNote.matchEntire(note.toString())?.groupValues?.get(2) ?: note.toString()
                    "<li>${format(body)}</li>"
                }
                "<h3>${escape(tag)}</h3><ul>$items</ul>"
            }
        }
        return "<html><body style='font-family: sans-serif; margin: 12px'>" +
            "<h2>What's New in TI-84 Evo ${escape(version)}</h2>$sections</body></html>"
    }

    private fun format(text: String): String = escape(text)
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
        .replace(Regex("\\[([^]]+)]\\((https://[^ )]+)\\)"), "<a href=\"$2\">$1</a>")

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
