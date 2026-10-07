package com.inspyresoftworks.ti84evo.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

class EvoRunConfigurationType : ConfigurationTypeBase(
    ID,
    "TI-84 Evo Python Project",
    "Push and launch a TI-Python project on a connected TI-84 Evo",
    IconLoader.getIcon("/icons/toolWindowIcon.svg", EvoRunConfigurationType::class.java),
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId(): String = ID

            override fun getOptionsClass(): Class<EvoRunConfigurationOptions> = EvoRunConfigurationOptions::class.java

            override fun createTemplateConfiguration(project: Project) =
                EvoRunConfiguration(project, this, "TI-84 Evo")
        })
    }

    companion object {
        const val ID = "TI84EvoPythonProject"
    }
}
