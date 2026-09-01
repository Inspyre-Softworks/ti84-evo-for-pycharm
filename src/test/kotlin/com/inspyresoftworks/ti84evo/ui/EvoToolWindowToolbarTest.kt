package com.inspyresoftworks.ti84evo.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EvoToolWindowToolbarTest : BasePlatformTestCase() {
    fun testToolbarUsesNativeOverflowAndDescribedIconActions() {
        val target = JPanel()
        val toolbar = EvoToolWindowToolbar.create(
            target,
            EvoToolWindowActions(
                refresh = {},
                readAttributes = {},
                browseFiles = {},
                captureScreen = {},
                uploadCurrentPython = {},
                uploadPicture = {},
                configureProject = {},
                configureTransfers = {},
                pushProject = {},
                showAbout = {},
            ),
        )

        assertSame(target, toolbar.targetComponent)
        assertTrue(toolbar.isReservePlaceAutoPopupIcon)

        val actions = toolbar.actionGroup.getChildren(null).filter { it.templatePresentation.text != null }
        assertEquals(
            setOf(
                "Refresh devices",
                "Read attributes",
                "Browse calculator files",
                "Capture screen",
                "Upload current Python file",
                "Upload picture",
                "Push project",
                "Configure project",
                "Transfer settings",
                "About TI-84 Evo",
            ),
            actions.mapTo(mutableSetOf()) { it.templatePresentation.text },
        )
        for (action in actions) {
            assertNotNull(action.templatePresentation.icon, action.templatePresentation.text)
            assertTrue(
                !action.templatePresentation.description.isNullOrBlank(),
                "${action.templatePresentation.text} needs a tooltip description",
            )
        }
    }
}
