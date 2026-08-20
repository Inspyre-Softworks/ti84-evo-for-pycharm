package com.inspyresoftworks.ti84evo.python

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary

/** Makes the bundled TI-84 Evo Python API stubs visible to PyCharm. */
class TiPythonLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val root = TiPythonStubs.root ?: return emptyList()
        return listOf(SyntheticLibrary.newImmutableLibrary(listOf(root)))
    }
}
