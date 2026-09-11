package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport

/** Uploads editable data envelopes and complete Evo variable files. */
class EvoVariableTransfer(transport: EvoTransport) {
    data class Result(
        val description: String,
        val payloadBytes: Int,
        val packets: Int,
        val archived: Boolean,
        val preservedListEditor: Boolean = false,
        val restoredBuiltInListEditor: Boolean = false,
    )

    data class ArchiveResult(
        val entry: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry,
        val payloadBytes: Int,
        val packets: Int,
    )

    private val transport = transport
    private val sender = EvoPythonTransfer(transport)
    private val link = EvoLink(transport)

    fun uploadEditable(value: EvoVariablePayload.EditableValue): Result {
        val result = if (value.kind == EvoVariablePayload.Kind.NUMBER) {
            uploadNumberNatively(value, link.getDirectory().map { it.name })
        } else if (value.kind == EvoVariablePayload.Kind.LIST) {
            val directory = link.getDirectory()
            val existing = directory.singleOrNull {
                it.type == EvoVariablePayload.Kind.LIST.typeId && it.name.equals(value.name, ignoreCase = true)
            }
            if (existing != null) {
                replaceListNatively(value, existing, directory.map { it.name })
            } else {
                reconnect()
                uploadEditableDirect(value)
            }
        } else {
            uploadEditableDirect(value)
        }
        if (value.kind == EvoVariablePayload.Kind.LIST && isPersistentBuiltInListName(value.name)) {
            reconnect()
            val editorPackets = EvoListEditor(transport).restoreDefaultColumns()
            return result.copy(
                packets = result.packets + editorPackets,
                restoredBuiltInListEditor = true,
            )
        }
        return result
    }

    private fun uploadNumberNatively(
        value: EvoVariablePayload.EditableValue,
        namesInUse: List<String>,
    ): Result {
        val temporaryName = temporaryListName(namesInUse)
        val temporaryValue = EvoVariablePayload.EditableValue(
            EvoVariablePayload.Kind.LIST,
            temporaryName,
            "{${EvoVariablePayload.normalizeValue(EvoVariablePayload.Kind.NUMBER, value.value)}}",
            archived = false,
        )
        reconnect()
        val temporaryUpload = uploadEditableDirect(temporaryValue)
        var temporaryEntry: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry? = null
        try {
            reconnect()
            temporaryEntry = link.getDirectory().singleOrNull {
                it.type == EvoVariablePayload.Kind.LIST.typeId && it.name.equals(temporaryName, ignoreCase = true)
            } ?: throw EvoProtocolException("temporary scalar list $temporaryName was not returned by the calculator")
            reconnect()
            val nativeNumber = EvoVariableFile.addChecksum(
                EvoVariableFile.numberFromSingleItemList(link.getVariable(temporaryEntry), value.name),
            )
            deleteTemporaryList(temporaryEntry)
            temporaryEntry = null
            reconnect()
            var packets = 0
            var uploadFailure: RuntimeException? = null
            try {
                packets = sender.uploadPayload(EvoVariablePayload.transferUrl(value.archived), nativeNumber)
            } catch (error: RuntimeException) {
                // Firmware 7.0 can commit a native scalar and then return DP for the
                // terminal B packet. Treat that as success only after reading the
                // exact value and requested memory location back from the device.
                uploadFailure = error
            }
            reconnect()
            val committed = runCatching {
                val entry = link.getDirectory().singleOrNull {
                    it.type == EvoVariablePayload.Kind.NUMBER.typeId &&
                        it.name.equals(value.name, ignoreCase = true) &&
                        it.archived == value.archived
                } ?: return@runCatching false
                reconnect()
                val decoded = EvoVariableDecoder.decode(
                    link.getVariable(entry),
                    entry.type,
                    entry.name,
                    entry.archived,
                )
                EvoVariablePayload.normalizeValue(value.kind, decoded.value) ==
                    EvoVariablePayload.normalizeValue(value.kind, value.value)
            }.getOrElse { verificationFailure ->
                uploadFailure?.addSuppressed(verificationFailure)
                false
            }
            if (!committed) {
                throw uploadFailure ?: EvoProtocolException(
                    "calculator did not retain number ${value.name} after upload",
                )
            }
            return Result(
                description = "Number ${value.name.uppercase()}",
                payloadBytes = nativeNumber.size,
                packets = temporaryUpload.packets + packets,
                archived = value.archived,
            )
        } finally {
            val cleanup = temporaryEntry ?: runCatching {
                reconnect()
                link.getDirectory().singleOrNull {
                    it.type == EvoVariablePayload.Kind.LIST.typeId && it.name.equals(temporaryName, ignoreCase = true)
                }
            }.getOrNull()
            cleanup?.let { runCatching { deleteTemporaryList(it) } }
        }
    }

    private fun uploadEditableDirect(value: EvoVariablePayload.EditableValue): Result {
        val payload = EvoVariablePayload.build(value)
        val packets = sender.uploadPayload(EvoVariablePayload.transferUrl(value.archived), payload)
        return Result(
            description = "${value.kind.wireName} ${value.name.uppercase()}",
            payloadBytes = payload.size,
            packets = packets,
            archived = value.archived,
        )
    }

    private fun replaceListNatively(
        value: EvoVariablePayload.EditableValue,
        existing: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry,
        namesInUse: List<String>,
    ): Result {
        val temporaryName = temporaryListName(namesInUse)
        val temporaryValue = value.copy(name = temporaryName, archived = false)
        reconnect()
        val temporaryUpload = uploadEditableDirect(temporaryValue)
        var temporaryEntry: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry? = null
        var temporaryRemoved = false

        try {
            reconnect()
            temporaryEntry = link.getDirectory().singleOrNull {
                it.type == EvoVariablePayload.Kind.LIST.typeId && it.name.equals(temporaryName, ignoreCase = true)
            } ?: throw EvoProtocolException("temporary list $temporaryName was not returned by the calculator")
            reconnect()
            val nativeTemporary = link.getVariable(temporaryEntry)
            val nativeTarget = EvoVariableFile.addChecksum(
                EvoVariableFile.retargetName(nativeTemporary, existing.tokenName),
            )

            // Remove only the unregistered scratch list before replacing the target. The
            // native type-1 replacement keeps the calculator's List Editor column binding.
            deleteTemporaryList(temporaryEntry)
            temporaryEntry = null
            temporaryRemoved = true
            reconnect()
            val packets = sender.uploadPayload(EvoVariablePayload.transferUrl(value.archived), nativeTarget)
            return Result(
                description = "${value.kind.wireName} ${value.name.uppercase()}",
                payloadBytes = nativeTarget.size,
                packets = temporaryUpload.packets + packets,
                archived = value.archived,
                preservedListEditor = true,
            )
        } finally {
            if (!temporaryRemoved) {
                val cleanupEntry = temporaryEntry ?: runCatching {
                    reconnect()
                    link.getDirectory().singleOrNull {
                        it.type == EvoVariablePayload.Kind.LIST.typeId &&
                            it.name.equals(temporaryName, ignoreCase = true)
                    }
                }.getOrNull()
                cleanupEntry?.let { runCatching { deleteTemporaryList(it) } }
            }
        }
    }

    fun uploadImage(image: EvoImagePayload.Built, archived: Boolean): Result {
        val payload = EvoVariableFile.addChecksum(image.bytes)
        var actualArchive = archived
        val packets = try {
            sender.uploadPayload(EvoImagePayload.transferUrl(image.name, archived), payload)
        } catch (error: RuntimeException) {
            if (archived || !isInvalidDataPayload(error)) throw error
            // Firmware 7.0 rejects type-8 Python image AppVars in RAM. Match the
            // calculator's storage constraint by retrying the native file in Archive.
            reconnect()
            actualArchive = true
            sender.uploadPayload(EvoImagePayload.transferUrl(image.name, archived = true), payload)
        }
        return Result("Image ${image.name}", payload.size, packets, actualArchive)
    }

    fun uploadFile(fileName: String, bytes: ByteArray, archived: Boolean): Result {
        require(bytes.isNotEmpty()) { "Calculator variable file is empty" }
        val url = "hh01/xfr/var?memtarget=${if (archived) 1 else 0}&policy=1"
        val packets = sender.uploadPayload(url, bytes)
        return Result(fileName, bytes.size, packets, archived)
    }

    /** Re-uploads existing RAM variables to Archive and verifies their new location. */
    fun archiveVariables(
        entries: List<com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry>,
        onProgress: (ArchiveResult, Int, Int) -> Unit = { _, _, _ -> },
    ): List<ArchiveResult> {
        val candidates = entries.filterNot { it.archived }
        val completed = mutableListOf<ArchiveResult>()
        for (entry in candidates) {
            val result = try {
                val raw = link.getVariable(entry)
                val file = EvoVariableFile.addChecksum(raw)
                reconnect()
                var packets = 0
                var uploadFailure: RuntimeException? = null
                try {
                    packets = sender.uploadPayload(archiveTransferUrl(), file)
                } catch (error: RuntimeException) {
                    // The calculator can commit the archive transfer even when the final ACK is lost.
                    // Verify the resulting directory state before reporting this entry as failed.
                    uploadFailure = error
                }
                reconnect()
                val archivedEntry = link.getDirectory().singleOrNull {
                    it.type == entry.type &&
                        it.archived &&
                        it.tokenName.contentEquals(entry.tokenName)
                } ?: throw EvoProtocolException(
                    "calculator did not report ${entry.name} in Archive after the transfer",
                    uploadFailure,
                )
                ArchiveResult(archivedEntry, file.size, packets)
            } catch (error: Throwable) {
                throw EvoVariableArchiveException(entry, completed.map { it.entry }, error)
            }
            completed += result
            runCatching { onProgress(result, completed.size, candidates.size) }
        }
        return completed
    }

    internal fun archiveTransferUrl(): String = "hh01/xfr/var?memtarget=1&policy=1"

    internal fun temporaryListName(namesInUse: Collection<String>): String {
        val occupied = namesInUse.mapTo(mutableSetOf()) { it.uppercase() }
        return (0..9999)
            .asSequence()
            .map { "Z%04d".format(it) }
            .firstOrNull { it !in occupied }
            ?: throw EvoProtocolException("no temporary calculator list name is available")
    }

    private fun deleteTemporaryList(entry: com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry) {
        var lastFailure: Throwable? = null
        repeat(3) { attempt ->
            try {
                link.deleteVariables(listOf(entry))
            } catch (error: RuntimeException) {
                lastFailure = error
            }
            val remains = runCatching {
                reconnect()
                link.getDirectory().any {
                    it.type == entry.type &&
                        (it.tokenName.contentEquals(entry.tokenName) || it.name.equals(entry.name, ignoreCase = true))
                }
            }.getOrElse { verificationFailure ->
                lastFailure = verificationFailure
                true
            }
            if (!remains) return
            if (attempt < 2) Thread.sleep(300L * (attempt + 1))
        }
        throw EvoProtocolException(
            "calculator still reports temporary list ${entry.name} after 3 delete attempts",
            lastFailure,
        )
    }

    private fun reconnect() {
        transport.close()
        transport.open()
    }

    private fun isInvalidDataPayload(error: RuntimeException): Boolean =
        error.message?.let { "DP" in it || "invalid data payload" in it.lowercase() } == true
}

internal fun isPersistentBuiltInListName(name: String): Boolean =
    name.uppercase() in PERSISTENT_BUILT_IN_LIST_NAMES

private val PERSISTENT_BUILT_IN_LIST_NAMES = (1..6).mapTo(mutableSetOf()) { "L$it" }
