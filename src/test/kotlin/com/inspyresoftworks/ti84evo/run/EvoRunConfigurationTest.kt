package com.inspyresoftworks.ti84evo.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvoRunConfigurationTest : BasePlatformTestCase() {
    fun testFactoryCreatesConfigurationWithProjectDefaults() {
        val type = EvoRunConfigurationType()
        val configuration = type.configurationFactories.single()
            .createTemplateConfiguration(project) as EvoRunConfiguration

        assertEquals(EvoProjectManifest.FILE_NAME, configuration.manifestPath)
        assertEquals("", configuration.programName)
        assertFalse(configuration.openLiveScreenViewer)
        assertNotNull(configuration.configurationEditor)
        assertNotNull(type.icon)
    }

    fun testRunOptionsRetainSelectedManifestAndProgram() {
        val type = EvoRunConfigurationType()
        val configuration = type.configurationFactories.single()
            .createTemplateConfiguration(project) as EvoRunConfiguration

        configuration.manifestPath = "calculator/.ti84-evo-project"
        configuration.programName = "MAIN"
        configuration.openLiveScreenViewer = true

        assertEquals("calculator/.ti84-evo-project", configuration.manifestPath)
        assertEquals("MAIN", configuration.programName)
        assertTrue(configuration.openLiveScreenViewer)
    }
}
