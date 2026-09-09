package com.inspyresoftworks.ti84evo.ui

import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class EvoDebugInfoTest {
    @Test
    fun `report includes useful diagnostics without user or filesystem properties`() {
        val report = EvoDebugInfo.render(
            status = EvoVersionStatus(
                installedVersion = "1.4.1",
                marketplaceVersion = "1.4.2",
                state = EvoVersionState.OUTDATED,
                detail = "A newer release exists.",
                installedHash = "installed-hash",
                marketplaceHash = "marketplace-hash",
            ),
            settings = EvoApplicationSettings.State(marketplaceCheckIntervalSeconds = 3_600),
            mischiefMode = true,
            ideName = "PyCharm",
            ideVersion = "2026.2",
            ideBuild = "PY-262.12345",
            generatedAt = OffsetDateTime.parse("2026-09-08T17:00:00-05:00"),
            properties = mapOf(
                "java.runtime.version" to "21.0.8",
                "java.vendor" to "JetBrains",
                "os.arch" to "amd64",
                "sun.arch.data.model" to "64",
                "os.name" to "Windows 11",
                "os.version" to "10.0",
                "user.name" to "sensitive-user",
                "user.home" to "C:\\Users\\sensitive-user",
            ),
        )

        assertContains(report, "**Marketplace Version:** 1.4.2")
        assertContains(report, "**Version Status:** OUTDATED")
        assertContains(report, "**Marketplace Validation:** Verified")
        assertContains(report, "**Mischief Mode:** Enabled")
        assertContains(report, "**Build/Commit:** SHA-256 installed-hash")
        assertContains(report, "**Marketplace Artifact SHA-256:** marketplace-hash")
        assertContains(report, "**Marketplace Check Interval:** 3600 seconds")
        assertContains(report, "2026-09-08T17:00:00-05:00")
        assertFalse(report.contains("sensitive-user"))
        assertFalse(report.contains("C:\\Users"))
    }
}
