package com.inspyresoftworks.ti84evo.ui

object EvoBuildInfo {
    val version: String by lazy {
        EvoBuildInfo::class.java.getResourceAsStream("/META-INF/ti84-evo-version.txt")
            ?.bufferedReader()
            ?.readText()
            ?.trim()
            ?: "unknown"
    }
}
