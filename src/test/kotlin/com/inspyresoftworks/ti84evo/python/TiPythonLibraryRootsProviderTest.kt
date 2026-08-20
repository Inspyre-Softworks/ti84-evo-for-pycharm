package com.inspyresoftworks.ti84evo.python

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TiPythonLibraryRootsProviderTest : BasePlatformTestCase() {
    fun testBundledStubsAreExposedAsAProjectLibrary() {
        val provider = TiPythonLibraryRootsProvider()
        val library = provider.getAdditionalProjectLibraries(project).single()
        val root = library.sourceRoots.single()

        assertEquals(TiPythonStubs.RESOURCE_ROOT, root.name)
        assertNotNull(root.findChild("ti_draw.pyi"))
        assertNotNull(root.findChild("ti_system.pyi"))
    }
}
