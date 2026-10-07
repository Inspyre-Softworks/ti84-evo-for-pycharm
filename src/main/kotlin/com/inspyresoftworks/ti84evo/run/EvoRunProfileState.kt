package com.inspyresoftworks.ti84evo.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment

class EvoRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: EvoRunConfiguration,
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler = EvoRunProcessHandler(configuration).also { it.start() }
}
