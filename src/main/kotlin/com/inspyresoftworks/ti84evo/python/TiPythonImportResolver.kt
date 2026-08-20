package com.inspyresoftworks.ti84evo.python

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.impl.PyImportResolver
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext

/** Resolves the calculator-provided modules to their bundled type declarations. */
class TiPythonImportResolver : PyImportResolver {
    override fun resolveImportReference(
        name: QualifiedName,
        context: PyQualifiedNameResolveContext,
        withRoots: Boolean,
    ): PsiElement? {
        if (name.componentCount != 1 || name.firstComponent !in TiPythonStubs.moduleNames) return null
        val root = TiPythonStubs.root ?: return null
        val stub = root.findChild("${name.firstComponent}.pyi") ?: return null
        return context.psiManager.findFile(stub)
    }
}
