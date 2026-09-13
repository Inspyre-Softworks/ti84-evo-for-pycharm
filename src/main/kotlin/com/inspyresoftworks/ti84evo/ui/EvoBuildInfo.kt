package com.inspyresoftworks.ti84evo.ui

import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Build metadata packaged without relying on IntelliJ's private plugin APIs. */
internal object EvoBuildInfo {
    val version: String by lazy {
        EvoBuildInfo::class.java.getResourceAsStream(VERSION_RESOURCE)
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

    /** Resolves the plugin root from a resource that is guaranteed to live in this plugin's JAR. */
    val installationDirectory: Path? by lazy {
        installationDirectoryFrom(EvoBuildInfo::class.java.getResource(VERSION_RESOURCE))
            ?: codeSourceInstallationDirectory()
    }

    internal fun installationDirectoryFrom(resource: URL?): Path? {
        if (resource?.protocol != "jar") return null
        return runCatching {
            val jarUri = URI.create(resource.toExternalForm().removePrefix("jar:").substringBefore("!/"))
            pluginRootFromCodeLocation(Path.of(jarUri))
        }.getOrNull()
    }

    private fun codeSourceInstallationDirectory(): Path? = runCatching {
        val location = Path.of(EvoBuildInfo::class.java.protectionDomain.codeSource.location.toURI())
        pluginRootFromCodeLocation(location)
    }.getOrNull()

    private fun pluginRootFromCodeLocation(location: Path): Path? {
        val containingDirectory = when {
            Files.isRegularFile(location) -> location.parent
            Files.isDirectory(location) -> location
            else -> null
        }
        return if (containingDirectory?.fileName?.toString() == "lib") {
            containingDirectory.parent
        } else {
            containingDirectory
        }?.toAbsolutePath()?.normalize()
    }

    private const val VERSION_RESOURCE = "/META-INF/ti84-evo-version.txt"
}
