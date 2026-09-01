package com.inspyresoftworks.ti84evo.ui

import java.nio.charset.StandardCharsets

/** Build metadata packaged without relying on IntelliJ's private plugin APIs. */
internal object EvoBuildInfo {
    val version: String by lazy {
        EvoBuildInfo::class.java.getResourceAsStream("/META-INF/ti84-evo-version.txt")
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText().trim() }
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"
    }
}
