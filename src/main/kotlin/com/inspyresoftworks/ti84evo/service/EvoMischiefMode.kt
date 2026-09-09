package com.inspyresoftworks.ti84evo.service

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Hidden developer mode controlled by an exact phrase in the user's profile. */
internal object EvoMischiefMode {
    const val ACTIVATION_PHRASE = "I solemnly swear that I am up to no good."
    const val MANAGED_PHRASE = "Mischief managed!"

    val lockFile: Path
        get() = Path.of(System.getProperty("user.home"), "mm.lock")

    fun isActive(): Boolean = isActive(lockFile)

    internal fun isActive(path: Path): Boolean = runCatching {
        if (!Files.isRegularFile(path)) return false
        val contents = Files.readString(path, StandardCharsets.UTF_8)
        val withoutOptionalNewline = when {
            contents.endsWith("\r\n") -> contents.dropLast(2)
            contents.endsWith('\n') -> contents.dropLast(1)
            else -> contents
        }
        withoutOptionalNewline == ACTIVATION_PHRASE
    }.getOrDefault(false)

    fun markManaged() {
        markManaged(lockFile)
    }

    internal fun markManaged(path: Path) {
        Files.writeString(path, MANAGED_PHRASE, StandardCharsets.UTF_8)
    }
}
