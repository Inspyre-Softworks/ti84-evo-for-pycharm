package com.inspyresoftworks.ti84evo.python

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/** Offers calculator-provided modules while a Python import is being typed. */
class TiPythonModuleCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val linePrefix = linePrefix(parameters)
                    if (!isModuleNamePosition(linePrefix)) return

                    for (module in TiPythonStubs.moduleNames) {
                        result.addElement(
                            LookupElementBuilder.create(module)
                                .withTypeText("TI Python module", true),
                        )
                    }
                }
            },
        )
    }

    private fun linePrefix(parameters: CompletionParameters): String {
        val source = parameters.originalFile.text
        val offset = parameters.offset.coerceIn(0, source.length)
        return linePrefix(source, offset)
    }

    private fun linePrefix(source: String, offset: Int): String {
        val lineStart = source.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        return source.substring(lineStart, offset)
    }

    private fun isModuleNamePosition(linePrefix: String): Boolean =
        FROM_MODULE.matches(linePrefix) || IMPORT_MODULE.matches(linePrefix)

    private companion object {
        val FROM_MODULE = Regex("""\s*from\s+[A-Za-z0-9_.]*""")
        val IMPORT_MODULE = Regex("""\s*import\s+(?:[A-Za-z0-9_.]+\s*,\s*)*[A-Za-z0-9_.]*""")
    }
}
