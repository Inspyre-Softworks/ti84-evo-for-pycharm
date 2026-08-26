package com.inspyresoftworks.ti84evo.python

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.impl.PyImportResolver
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TiPythonImportResolverTest : BasePlatformTestCase() {
    fun testFromImportRequestsAutomaticMemberPopupAfterSpace() {
        val contributor = TiPythonModuleCompletionContributor()
        val tiFile = myFixture.configureByText(PythonFileType.INSTANCE, "from ti_draw import")
        val tiPosition = tiFile.findElementAt(tiFile.textLength - 1)
            ?: error("TI import position was not parsed")
        assertTrue(contributor.invokeAutoPopup(tiPosition, ' '))

        val desktopFile = myFixture.configureByText(PythonFileType.INSTANCE, "from pathlib import")
        val desktopPosition = desktopFile.findElementAt(desktopFile.textLength - 1)
            ?: error("Desktop import position was not parsed")
        assertTrue(!contributor.invokeAutoPopup(desktopPosition, ' '))
    }

    fun testTiModuleNamesCompleteWhileTypingFromImport() {
        registerModuleCompletionContributor()
        myFixture.configureByText(PythonFileType.INSTANCE, "from ti_<caret>\n")
        myFixture.completeBasic()

        val completions = myFixture.lookupElementStrings.orEmpty().toSet()
        assertTrue(
            completions.containsAll(TiPythonStubs.moduleNames),
            "Missing TI modules ${TiPythonStubs.moduleNames - completions} in $completions",
        )
    }

    fun testTiModuleNamesCompleteWhileTypingImport() {
        registerModuleCompletionContributor()
        myFixture.configureByText(PythonFileType.INSTANCE, "import ti_<caret>\n")
        myFixture.completeBasic()

        val completions = myFixture.lookupElementStrings.orEmpty().toSet()
        assertTrue(
            completions.containsAll(TiPythonStubs.moduleNames),
            "Missing TI modules ${TiPythonStubs.moduleNames - completions} in $completions",
        )
    }

    fun testEveryTiModuleOffersMembersInFromImport() {
        registerModuleCompletionContributor()
        registerImportResolver()
        val expectedMembers = mapOf(
            "ti_draw" to "fill_rect",
            "ti_hub" to "connect",
            "ti_image" to "show_image",
            "ti_plotlib" to "scatter",
            "ti_rover" to "forward",
            "ti_system" to "wait_key",
        )

        for ((module, member) in expectedMembers) {
            myFixture.configureByText(PythonFileType.INSTANCE, "from $module import <caret>\n")
            myFixture.completeBasic()

            val completions = myFixture.lookupElementStrings.orEmpty()
            assertTrue(member in completions, "Missing $member from $module import: $completions")
            assertEquals(
                1,
                completions.count { it == member },
                "$module.$member should be offered exactly once: $completions",
            )
        }
    }

    fun testFromImportMemberCompletionFiltersByTypedPrefix() {
        registerModuleCompletionContributor()
        registerImportResolver()
        myFixture.configureByText(PythonFileType.INSTANCE, "from ti_draw import fill_<caret>\n")
        myFixture.completeBasic()

        val completions = myFixture.lookupElementStrings.orEmpty()
        assertTrue("fill_circle" in completions, "Missing fill_circle in $completions")
        assertTrue("fill_poly" in completions, "Missing fill_poly in $completions")
        assertTrue("fill_rect" in completions, "Missing fill_rect in $completions")
        assertTrue("clear" !in completions, "Prefix filter leaked clear into $completions")
    }

    fun testEveryTiModuleImportResolvesToBundledStub() {
        registerImportResolver()

        for (module in TiPythonStubs.moduleNames) {
            val file = myFixture.configureByText(PythonFileType.INSTANCE, "from $module import *\n")
            val importSource = PsiTreeUtil.findChildOfType(file, PyReferenceExpression::class.java)
                ?: error("$module import source was not parsed")
            val reference: PsiReference = (importSource as PsiElement).reference
                ?: error("$module import has no PSI reference")
            val resolved = reference.resolve() ?: error("$module import did not resolve")

            assertIs<PyFile>(resolved)
            assertEquals("$module.pyi", resolved.name)
        }
    }

    fun testEveryTiModuleOffersMemberCompletionFromBundledStub() {
        registerImportResolver()
        val expectedMembers = mapOf(
            "ti_draw" to "fill_rect",
            "ti_hub" to "connect",
            "ti_image" to "show_image",
            "ti_plotlib" to "scatter",
            "ti_rover" to "forward",
            "ti_system" to "wait_key",
        )

        for ((module, member) in expectedMembers) {
            myFixture.configureByText(PythonFileType.INSTANCE, "import $module\n$module.<caret>\n")
            myFixture.completeBasic()

            val completions = myFixture.lookupElementStrings.orEmpty()
            assertTrue(member in completions, "Missing $module.$member in $completions")
        }
    }

    private fun registerModuleCompletionContributor() {
        val pluginDescriptor = DefaultPluginDescriptor(
            PluginId.getId("com.inspyresoftworks.ti84evo.test"),
            TiPythonModuleCompletionContributor::class.java.classLoader,
        )
        val extension = CompletionContributorEP(
            "Python",
            TiPythonModuleCompletionContributor::class.java.name,
            pluginDescriptor,
        )
        CompletionContributor.EP.point.registerExtension(extension, testRootDisposable)
    }

    private fun registerImportResolver() {
        PyImportResolver.EP_NAME.point.registerExtension(TiPythonImportResolver(), testRootDisposable)
    }
}
