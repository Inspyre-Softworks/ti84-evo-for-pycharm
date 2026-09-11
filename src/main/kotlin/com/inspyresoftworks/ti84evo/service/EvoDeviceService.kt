package com.inspyresoftworks.ti84evo.service

import com.intellij.openapi.components.Service
import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry
import com.inspyresoftworks.ti84evo.model.EvoScreenCapture
import com.inspyresoftworks.ti84evo.protocol.EvoImagePayload
import com.inspyresoftworks.ti84evo.protocol.EvoLink
import com.inspyresoftworks.ti84evo.protocol.EvoPythonTransfer
import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectPuller
import com.inspyresoftworks.ti84evo.protocol.EvoPythonProjectVerifier
import com.inspyresoftworks.ti84evo.protocol.EvoVariablePayload
import com.inspyresoftworks.ti84evo.protocol.EvoVariableTransfer
import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import com.inspyresoftworks.ti84evo.transport.EvoSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Project-lifetime Evo operations.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
@Service(Service.Level.PROJECT)
class EvoDeviceService(private val coroutineScope: CoroutineScope) {
    data class PythonProjectState(
        val directory: List<EvoDirectoryEntry>,
        val sources: Map<String, String>,
    )
    data class ImageUploadResult(
        val image: EvoImagePayload.Built,
        val transfer: EvoVariableTransfer.Result,
    )

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

    fun readPythonProjectState(
        programs: List<EvoPythonTransfer.Program>,
        callback: (Result<PythonProjectState>) -> Unit,
    ) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        val directory = EvoLink(transport).getDirectory()
                        val sources = if (programs.isEmpty()) emptyMap() else
                            EvoPythonProjectVerifier(transport).readSources(directory, programs)
                        PythonProjectState(directory, sources)
                    }
                }
            }
            callback(result)
        }
    }

    fun captureScreen(callback: (Result<EvoScreenCapture>) -> Unit) {
        runLinkOperation({ it.getScreenCapture() }, callback)
    }

    fun readVariable(entry: EvoDirectoryEntry, callback: (Result<ByteArray>) -> Unit) {
        runLinkOperation({ it.getVariable(entry) }, callback)
    }

    fun deleteVariables(
        entries: List<EvoDirectoryEntry>,
        onProgress: (EvoDirectoryEntry, Int, Int) -> Unit = { _, _, _ -> },
        callback: (Result<List<EvoDirectoryEntry>>) -> Unit,
    ) {
        runLinkOperation({ it.deleteVariables(entries, onProgress) }, callback)
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

    fun pullPythonProject(
        onProgress: (EvoPythonProjectPuller.Program, Int, Int) -> Unit = { _, _, _ -> },
        callback: (Result<EvoPythonProjectPuller.Result>) -> Unit,
    ) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        EvoPythonProjectPuller(transport).pull(onProgress)
                    }
                }
            }
            callback(result)
        }
    }

    fun archiveVariables(
        entries: List<EvoDirectoryEntry>,
        onProgress: (EvoVariableTransfer.ArchiveResult, Int, Int) -> Unit = { _, _, _ -> },
        callback: (Result<List<EvoVariableTransfer.ArchiveResult>>) -> Unit,
    ) = runTransferOperation({ EvoVariableTransfer(it).archiveVariables(entries, onProgress) }, callback)

    fun uploadEditableVariable(
        value: EvoVariablePayload.EditableValue,
        callback: (Result<EvoVariableTransfer.Result>) -> Unit,
    ) = runTransferOperation({ EvoVariableTransfer(it).uploadEditable(value) }, callback)

    fun uploadImage(
        path: Path,
        name: String,
        archived: Boolean,
        settings: EvoApplicationSettings.State,
        callback: (Result<ImageUploadResult>) -> Unit,
    ) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = ImageIO.read(path.toFile())
                        ?: error("Unsupported or unreadable image: ${path.fileName}")
                    val image = EvoImagePayload.build(source, name, settings)
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        ImageUploadResult(image, EvoVariableTransfer(transport).uploadImage(image, archived))
                    }
                }
            }
            callback(result)
        }
    }

    fun uploadVariableFile(
        path: Path,
        archived: Boolean,
        callback: (Result<EvoVariableTransfer.Result>) -> Unit,
    ) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = Files.readAllBytes(path)
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        EvoVariableTransfer(transport).uploadFile(path.fileName.toString(), bytes, archived)
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

    private fun <T> runTransferOperation(
        operation: (EvoSerialTransport) -> T,
        callback: (Result<T>) -> Unit,
    ) {
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    EvoSerialTransport.auto().use { transport ->
                        transport.open()
                        operation(transport)
                    }
                }
            }
            callback(result)
        }
    }
}
