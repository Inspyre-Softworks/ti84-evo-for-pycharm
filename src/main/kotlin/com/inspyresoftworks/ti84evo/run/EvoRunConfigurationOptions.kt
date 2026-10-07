package com.inspyresoftworks.ti84evo.run

import com.intellij.execution.configurations.RunConfigurationOptions
import com.inspyresoftworks.ti84evo.project.EvoProjectManifest

class EvoRunConfigurationOptions : RunConfigurationOptions() {
    var manifestPath by string(EvoProjectManifest.FILE_NAME)
    var programName by string("")
    var openLiveScreenViewer by property(false)
}
