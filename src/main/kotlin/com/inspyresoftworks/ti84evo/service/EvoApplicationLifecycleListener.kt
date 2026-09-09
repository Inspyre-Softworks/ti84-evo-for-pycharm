package com.inspyresoftworks.ti84evo.service

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages

/** Offers to deactivate Mischief Mode before the IDE closes. */
internal class EvoApplicationLifecycleListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
        if (!EvoMischiefMode.isActive()) return

        val answer = Messages.showYesNoDialog(
            "Mischief managed?",
            "Mischief Managed",
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return

        runCatching(EvoMischiefMode::markManaged)
            .onFailure { LOG.warn("Could not deactivate Mischief Mode", it) }
    }

    companion object {
        private val LOG = Logger.getInstance(EvoApplicationLifecycleListener::class.java)
    }
}
