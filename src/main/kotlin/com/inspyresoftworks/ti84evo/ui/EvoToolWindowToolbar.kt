package com.inspyresoftworks.ti84evo.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import javax.swing.Icon
import javax.swing.JComponent

internal data class EvoToolWindowActions(
    val refresh: () -> Unit,
    val readAttributes: () -> Unit,
    val captureScreen: () -> Unit,
    val uploadCurrentPython: () -> Unit,
    val configureProject: () -> Unit,
    val pushProject: () -> Unit,
)

/** Builds a native IDE toolbar with a visible overflow affordance. */
internal object EvoToolWindowToolbar {
    const val PLACE = "TI84Evo.ToolWindow"

    @Suppress("DEPRECATION")
    fun create(target: JComponent, callbacks: EvoToolWindowActions): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(toolbarAction(
                "Refresh devices",
                "Scan for connected TI-84 Evo calculators",
                AllIcons.Actions.Refresh,
                callbacks.refresh,
            ))
            add(toolbarAction(
                "Read attributes",
                "Read system attributes from the connected calculator",
                AllIcons.Actions.Show,
                callbacks.readAttributes,
            ))
            add(toolbarAction(
                "Capture screen",
                "Capture the calculator display",
                AllIcons.Actions.ViewAsImage,
                callbacks.captureScreen,
            ))
            addSeparator()
            add(toolbarAction(
                "Upload current Python file",
                "Upload the active .py file to the calculator",
                AllIcons.Actions.Upload,
                callbacks.uploadCurrentPython,
            ))
            add(toolbarAction(
                "Push project",
                "Upload every file declared in the TI-84 Evo project manifest",
                AllIcons.Actions.RunAll,
                callbacks.pushProject,
            ))
            addSeparator()
            add(toolbarAction(
                "Configure project",
                "Choose the Python files included in the calculator project",
                AllIcons.General.Settings,
                callbacks.configureProject,
            ))
        }

        return ActionManager.getInstance().createActionToolbar(PLACE, group, true).apply {
            setTargetComponent(target)
            setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY)
            setReservePlaceAutoPopupIcon(true)
            setSecondaryActionsTooltip("More TI-84 Evo actions")
        }
    }

    private fun toolbarAction(
        text: String,
        description: String,
        icon: Icon,
        callback: () -> Unit,
    ): AnAction = object : AnAction(text, description, icon) {
        override fun actionPerformed(event: AnActionEvent) = callback()
    }
}
