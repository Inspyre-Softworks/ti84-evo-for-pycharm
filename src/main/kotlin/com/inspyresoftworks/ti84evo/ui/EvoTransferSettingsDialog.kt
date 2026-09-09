package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/** User-level image transfer preferences stored outside individual projects. */
internal class EvoTransferSettingsDialog(
    private val project: Project,
    private val settings: EvoApplicationSettings,
    private val mischiefMode: Boolean,
    private val onSaved: () -> Unit,
) : DialogWrapper(project) {
    private val initial = settings.snapshot(mischiefMode)
    private val optimize = JCheckBox("Reduce image dimensions and color count before transfer", initial.optimizeImages)
    private val maxWidthSpinner = JSpinner(SpinnerNumberModel(initial.imageMaxWidth, 16, 320, 1))
    private val maxHeightSpinner = JSpinner(SpinnerNumberModel(initial.imageMaxHeight, 16, 210, 1))
    private val colors = JComboBox(EvoApplicationSettings.SUPPORTED_COLOR_COUNTS.toTypedArray()).apply {
        selectedItem = initial.imageColors
    }
    private val marketplaceInterval = JSpinner(
        SpinnerNumberModel(
            initial.marketplaceCheckIntervalSeconds,
            EvoApplicationSettings.minimumMarketplaceCheckIntervalSeconds(mischiefMode),
            Int.MAX_VALUE,
            1,
        ),
    )

    init {
        title = "TI-84 Evo Transfer Settings"
        setOKButtonText("Save Settings")
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(GridBagLayout()).apply {
        border = JBUI.Borders.empty(12)
        add(optimize, constraints(0, 0, width = 2))
        add(JBLabel("Maximum width:"), constraints(0, 1))
        add(maxWidthSpinner, constraints(1, 1, fill = GridBagConstraints.HORIZONTAL, weight = 1.0))
        add(JBLabel("Maximum height:"), constraints(0, 2))
        add(maxHeightSpinner, constraints(1, 2, fill = GridBagConstraints.HORIZONTAL, weight = 1.0))
        add(JBLabel("Maximum colors:"), constraints(0, 3))
        add(colors, constraints(1, 3, fill = GridBagConstraints.HORIZONTAL, weight = 1.0))
        add(
            JBLabel("Images keep their aspect ratio and use Evo IM8C run-length compression."),
            constraints(0, 4, width = 2),
        )
        add(JBLabel("Marketplace check interval (seconds):"), constraints(0, 5))
        add(marketplaceInterval, constraints(1, 5, fill = GridBagConstraints.HORIZONTAL, weight = 1.0))
        add(
            JBLabel(
                if (mischiefMode) "Mischief Mode permits intervals below 60 seconds."
                else "The minimum Marketplace check interval is 60 seconds.",
            ),
            constraints(0, 6, width = 2, weightY = 1.0, anchor = GridBagConstraints.NORTHWEST),
        )
    }

    override fun doOKAction() {
        val candidate = EvoApplicationSettings.State(
            optimizeImages = optimize.isSelected,
            imageMaxWidth = maxWidthSpinner.value as Int,
            imageMaxHeight = maxHeightSpinner.value as Int,
            imageColors = colors.selectedItem as Int,
            marketplaceCheckIntervalSeconds = marketplaceInterval.value as Int,
        )
        runCatching { settings.update(candidate, mischiefMode) }
            .onSuccess {
                onSaved()
                super.doOKAction()
            }
            .onFailure { Messages.showErrorDialog(project, it.message ?: "Invalid transfer settings", title) }
    }

    private fun constraints(
        x: Int,
        y: Int,
        width: Int = 1,
        fill: Int = GridBagConstraints.NONE,
        weight: Double = 0.0,
        weightY: Double = 0.0,
        anchor: Int = GridBagConstraints.WEST,
    ) = GridBagConstraints().apply {
        gridx = x
        gridy = y
        gridwidth = width
        this.fill = fill
        weightx = weight
        weighty = weightY
        this.anchor = anchor
        insets = Insets(5, 5, 5, 12)
    }
}
