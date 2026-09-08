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
    val completedEntries: List<EvoDirectoryEntry>,
    cause: Throwable,
) : EvoProtocolException(
    "Failed to ${if (isPersistentBuiltInList(failedEntry)) "clear" else "delete"} ${failedEntry.name} " +
        "after completing ${completedEntries.size} calculator file operations",
    cause,
) {
    val deletedEntries: List<EvoDirectoryEntry> = completedEntries.filterNot(::isPersistentBuiltInList)
    val clearedEntries: List<EvoDirectoryEntry> = completedEntries.filter(::isPersistentBuiltInList)
}

class EvoVariableArchiveException(
    val failedEntry: EvoDirectoryEntry,
    val archivedEntries: List<EvoDirectoryEntry>,
    cause: Throwable,
) : EvoProtocolException(
    "Failed to archive ${failedEntry.name} after archiving ${archivedEntries.size} calculator files",
    cause,
)
