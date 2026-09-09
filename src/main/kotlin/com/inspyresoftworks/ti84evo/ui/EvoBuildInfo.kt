package com.inspyresoftworks.ti84evo.ui

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Build metadata packaged without relying on IntelliJ's private plugin APIs. */
internal object EvoBuildInfo {
    val version: String by lazy {
        EvoBuildInfo::class.java.getResourceAsStream("/META-INF/ti84-evo-version.txt")
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText().trim() }
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"
    }

    val commit: String? by lazy {
        EvoBuildInfo::class.java.getResourceAsStream("/META-INF/ti84-evo-commit.txt")
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText().trim() }
            ?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }
            ?: System.getProperty("ti84.evo.commit")
                ?.trim()
                ?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }
    }

    /** Resolves the plugin root from the code source without relying on IntelliJ internal APIs. */
    val installationDirectory: Path? by lazy {
        runCatching {
            val location = Path.of(EvoBuildInfo::class.java.protectionDomain.codeSource.location.toURI())
            val containingDirectory = when {
                Files.isRegularFile(location) -> location.parent
                Files.isDirectory(location) -> location
                else -> null
            }
            if (containingDirectory?.fileName?.toString() == "lib") {
                containingDirectory.parent
            } else {
                containingDirectory
            }
        }.getOrNull()
    }
}
