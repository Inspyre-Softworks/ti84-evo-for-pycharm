package com.inspyresoftworks.ti84evo.ui

import com.intellij.openapi.application.ApplicationInfo
import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Produces a sanitized, clipboard-ready Markdown diagnostic report. */
internal object EvoDebugInfo {
    fun create(
        status: EvoVersionStatus,
        settings: EvoApplicationSettings.State,
        mischiefMode: Boolean,
        generatedAt: OffsetDateTime = OffsetDateTime.now(),
    ): String {
        val application = ApplicationInfo.getInstance()
        return render(
            status = status,
            settings = settings,
            mischiefMode = mischiefMode,
            ideName = application.fullApplicationName,
            ideVersion = application.fullVersion,
            ideBuild = application.build.asString(),
            generatedAt = generatedAt,
        )
    }

    internal fun render(
        status: EvoVersionStatus,
        settings: EvoApplicationSettings.State,
        mischiefMode: Boolean,
        ideName: String,
        ideVersion: String,
        ideBuild: String,
        generatedAt: OffsetDateTime,
        properties: Map<String, String> = System.getProperties().stringPropertyNames()
            .associateWith { System.getProperty(it, "") },
    ): String = buildString {
        appendLine("# TI84-Evo-For-PyCharm Debug Information")
        appendLine()
        appendLine("## Plugin")
        appendLine()
        appendLine("- **Name:** TI-84 Evo")
        appendLine("- **Installed Version:** ${safe(status.installedVersion)}")
        appendLine("- **Marketplace Version:** ${safe(status.marketplaceVersion ?: "Unknown")}")
        appendLine("- **Version Status:** ${status.statusDescription}")
        appendLine("- **Marketplace Validation:** ${status.validationDescription}")
        appendLine("- **Plugin ID:** ${com.inspyresoftworks.ti84evo.service.EvoMarketplaceService.PLUGIN_ID}")
        when {
            EvoBuildInfo.commit != null -> appendLine("- **Build/Commit:** ${safe(EvoBuildInfo.commit!!)}")
            status.installedHash != null -> appendLine("- **Build/Commit:** SHA-256 ${safe(status.installedHash)}")
        }
        status.marketplaceHash?.let { appendLine("- **Marketplace Artifact SHA-256:** ${safe(it)}") }
        appendLine("- **Mischief Mode:** ${if (mischiefMode) "Enabled" else "Disabled"}")
        appendLine()
        appendLine("## IDE")
        appendLine()
        appendLine("- **IDE:** ${safe(ideName)}")
        appendLine("- **Version:** ${safe(ideVersion)}")
        appendLine("- **Build:** ${safe(ideBuild)}")
        appendLine()
        appendLine("## Runtime")
        appendLine()
        appendLine("- **Java/JBR:** ${property(properties, "java.runtime.version", "java.version")}")
        appendLine("- **Vendor:** ${property(properties, "java.vendor")}")
        appendLine("- **JVM Architecture:** ${property(properties, "os.arch")}")
        appendLine("- **JVM Data Model:** ${property(properties, "sun.arch.data.model")}-bit")
        appendLine("- **Kotlin Runtime:** ${KotlinVersion.CURRENT}")
        appendLine()
        appendLine("## Operating System")
        appendLine()
        appendLine("- **OS:** ${property(properties, "os.name")}")
        appendLine("- **Version:** ${property(properties, "os.version")}")
        appendLine("- **Architecture:** ${property(properties, "os.arch")}")
        appendLine()
        appendLine("## Plugin Configuration")
        appendLine()
        appendLine("- **Marketplace Check Interval:** ${settings.marketplaceCheckIntervalSeconds} seconds")
        appendLine("- **Optimize Images:** ${settings.optimizeImages}")
        appendLine("- **Maximum Image Size:** ${settings.imageMaxWidth} x ${settings.imageMaxHeight}")
        appendLine("- **Maximum Image Colors:** ${settings.imageColors}")
        appendLine()
        appendLine("## Generated")
        appendLine()
        appendLine("- **Timestamp:** ${generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
    }.trimEnd()

    private fun property(properties: Map<String, String>, vararg names: String): String =
        names.firstNotNullOfOrNull { properties[it]?.takeIf(String::isNotBlank) }
            ?.let(::safe)
            ?: "Unknown"

    private fun safe(value: String): String = value
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace(Regex("[\\p{Cntrl}&&[^\\t]]"), "")
        .trim()
}
