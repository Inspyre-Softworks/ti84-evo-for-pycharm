package com.inspyresoftworks.ti84evo.python

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.PythonLanguage

/** Opens TI module-member completion after typing the space in `from ti_* import `. */
class TiPythonAutoPopupTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (!file.language.isKindOf(PythonLanguage.INSTANCE)) return Result.CONTINUE
        if (!shouldScheduleAutoPopup(editor.document.text, editor.caretModel.offset, charTyped)) {
            return Result.CONTINUE
        }

        AutoPopupController.getInstance(project).scheduleAutoPopup(editor, null)
        return Result.STOP
    }

    internal companion object {
        private val FROM_MEMBER_TRIGGER = Regex(
            """\s*from\s+(?:${TiPythonStubs.moduleNames.joinToString("|") { Regex.escape(it) }})\s+import\s*""",
        )

        fun shouldScheduleAutoPopup(source: String, offset: Int, charTyped: Char): Boolean {
            if (charTyped != ' ') return false
            val safeOffset = offset.coerceIn(0, source.length)
            val lineStart = if (safeOffset == 0) {
                0
            } else {
                source.lastIndexOf('\n', safeOffset - 1).let { if (it < 0) 0 else it + 1 }
            }
            return FROM_MEMBER_TRIGGER.matches(source.substring(lineStart, safeOffset))
        }
    }
}
