package com.inspyresoftworks.ti84evo.service

import com.intellij.openapi.components.Service
import com.inspyresoftworks.ti84evo.model.EvoScreenCapture
import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.protocol.EvoLink
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.transport.EvoSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Project-lifetime Evo operations.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
@Service(Service.Level.PROJECT)
class EvoDeviceService(private val coroutineScope: CoroutineScope) {
    fun detect(callback: (Result<List<String>>) -> Unit) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EvoSerialTransport.detectedPorts().map {
                        "${it.systemPortName} — ${it.descriptivePortName}"
                    }
                }
            }
            callback(result)
        }
    }

    fun readAttributes(callback: (Result<Map<String, Any?>>) -> Unit) {
        runLinkOperation({ it.getAttributes() }, callback)
    }

    fun readDirectory(callback: (Result<List<EvoDirectoryEntry>>) -> Unit) {
        runLinkOperation({ it.getDirectory() }, callback)
    }

    fun captureScreen(callback: (Result<EvoScreenCapture>) -> Unit) {
        runLinkOperation({ it.getScreenCapture() }, callback)
    }

    fun deleteVariables(
        entries: List<EvoDirectoryEntry>,
        callback: (Result<List<EvoDirectoryEntry>>) -> Unit,
    ) {
        runLinkOperation({ it.deleteVariables(entries) }, callback)
    }

    fun uploadPython(
        source: String,
        programName: String,
        archive: Boolean = false,
        callback: (Result<EvoPythonTransfer.Result>) -> Unit,
    ) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        EvoPythonTransfer(transport).upload(
                            programName = programName,
                            source = source,
                            archive = archive,
                            overwrite = true,
                        )
                    }
                }
            }
            callback(result)
        }
    }

    fun uploadPythonProject(
        programs: List<EvoPythonTransfer.Program>,
        onProgress: (EvoPythonTransfer.Result, Int, Int) -> Unit = { _, _, _ -> },
        callback: (Result<EvoPythonTransfer.ProjectResult>) -> Unit,
    ) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        EvoPythonTransfer(transport).uploadProject(
                            programs = programs,
                            overwrite = true,
                            onProgress = onProgress,
                        )
                    }
                }
            }
            callback(result)
        }
    }

    private fun <T> runLinkOperation(operation: (EvoLink) -> T, callback: (Result<T>) -> Unit) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        operation(EvoLink(transport))
                    }
                }
            }
            callback(result)
        }
    }
}
