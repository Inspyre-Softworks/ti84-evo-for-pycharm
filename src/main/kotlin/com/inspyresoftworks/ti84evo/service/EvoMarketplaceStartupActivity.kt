package com.inspyresoftworks.ti84evo.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Starts application-level Marketplace polling as soon as a project opens. */
internal class EvoMarketplaceStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().getService(EvoMarketplaceService::class.java)
    }
}
