package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry

/**
 * Base exception for TI-84 Evo protocol failures.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
open class EvoProtocolException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class EvoFrameException(message: String) : EvoProtocolException(message)
class EvoTimeoutException(message: String) : EvoProtocolException(message)
class EvoUnexpectedFrameException(message: String) : EvoProtocolException(message)
class EvoUnsupportedException(message: String) : EvoProtocolException(message)

class EvoVariableDeleteException(
    val failedEntry: EvoDirectoryEntry,
    val deletedEntries: List<EvoDirectoryEntry>,
    cause: Throwable,
) : EvoProtocolException(
    "Failed to delete ${failedEntry.name} after deleting ${deletedEntries.size} calculator files",
    cause,
)
