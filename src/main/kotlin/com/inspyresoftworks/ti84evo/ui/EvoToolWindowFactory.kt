package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.inspyresoftworks.ti84evo.service.EvoMischiefMode
import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

/**
 * Creates the TI-84 Evo tool window.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = EvoToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        val component = toolWindow.component
        var showing = false
        var generation = 0
        fun onVisibilityChanged() {
            val nowShowing = component.isShowing
            if (nowShowing == showing) return
            showing = nowShowing
            val currentGeneration = ++generation
            if (!nowShowing) return
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || !component.isShowing || generation != currentGeneration) return@invokeLater
                val html = EvoChangelog.currentVersionHtml() ?: return@invokeLater
                val settings = ApplicationManager.getApplication().getService(EvoApplicationSettings::class.java)
                val mischiefMode = EvoMischiefMode.isActive()
                if (settings.claimChangelog(EvoBuildInfo.version, mischiefMode)) {
                    EvoChangelogDialog(project, html, mischiefMode, settings).show()
                }
            }
        }
        val listener = HierarchyListener { event ->
            if ((event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) onVisibilityChanged()
        }
        component.addHierarchyListener(listener)
        Disposer.register(panel) { component.removeHierarchyListener(listener) }
        onVisibilityChanged()
    }
}
