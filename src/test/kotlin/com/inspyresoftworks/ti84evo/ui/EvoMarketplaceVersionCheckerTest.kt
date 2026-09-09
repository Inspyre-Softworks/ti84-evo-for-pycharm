package com.inspyresoftworks.ti84evo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvoMarketplaceVersionCheckerTest {
    @Test
    fun `only a newer installed version is developmental`() {
        val status = resolve(installed = "0.3.1", claimed = "0.3.0")

        assertEquals(EvoVersionState.DEVELOPMENTAL, status.state)
        assertEquals("[DEVELOPMENTAL]", status.tag)
        assertEquals("v0.3.1 - [DEVELOPMENTAL]", status.footerText)
    }

    @Test
    fun `an older installed version is outdated without a developmental tag`() {
        val status = resolve(installed = "0.2.9", claimed = "0.3.0")

        assertEquals(EvoVersionState.OUTDATED, status.state)
        assertEquals("[OUTDATED]", status.tag)
        assertEquals("0.2.9 - [OUTDATED]", status.displayVersion)
    }

    @Test
    fun `equal versions with different hashes are marked as a mismatch`() {
        val status = resolve(installed = "0.3.0", claimed = "0.3.0", installedHash = "local")

        assertEquals(EvoVersionState.MISMATCH, status.state)
        assertEquals("[DEVELOPMENTAL]", status.tag)
        assertEquals("0.3.0 - [DEVELOPMENTAL]", status.displayVersion)
        assertTrue(status.detail.contains("hashes differ"))
    }

    @Test
    fun `unknown marketplace state replaces comparison labels with unverified`() {
        val checking = EvoVersionStatus.checking("1.4.2")
        val unavailable = checking.copy(
            state = EvoVersionState.UNAVAILABLE,
            detail = "No connection",
        )

        assertEquals("1.4.2 [UNVERIFIED]", checking.displayVersion)
        assertEquals("1.4.2 [UNVERIFIED]", unavailable.displayVersion)
        assertTrue(checking.isUnverified)
        assertTrue(unavailable.isUnverified)
        assertEquals("UNVERIFIED", unavailable.statusDescription)
    }

    @Test
    fun `current version has no status suffix`() {
        val status = resolve(installed = "1.4.2", claimed = "1.4.2")

        assertEquals(EvoVersionState.CURRENT, status.state)
        assertEquals("1.4.2", status.displayVersion)
        assertEquals(null, status.tag)
    }

    @Test
    fun `marketplace embedded version must match its public claim`() {
        val status = EvoMarketplaceVersionChecker.resolve(
            installedVersion = "0.3.1",
            installedDescriptorVersion = "0.3.1",
            installedHash = "local",
            marketplace = EvoMarketplaceArtifact("0.3.0", "0.2.9", "market"),
        )

        assertEquals(EvoVersionState.MISMATCH, status.state)
        assertTrue(status.detail.contains("embeds 0.2.9"))
    }

    @Test
    fun `installed embedded version must match its bundled version`() {
        val status = EvoMarketplaceVersionChecker.resolve(
            installedVersion = "0.3.0",
            installedDescriptorVersion = "0.2.9",
            installedHash = "market",
            marketplace = EvoMarketplaceArtifact("0.3.0", "0.3.0", "market"),
        )

        assertEquals(EvoVersionState.MISMATCH, status.state)
        assertTrue(status.detail.contains("installed JAR embeds version 0.2.9"))
    }

    @Test
    fun `semantic prereleases compare below releases`() {
        assertEquals(-1, EvoMarketplaceVersionChecker.compareSemanticVersions("1.0.0-rc.1", "1.0.0"))
        assertEquals(1, EvoMarketplaceVersionChecker.compareSemanticVersions("1.0.1", "1.0.0"))
        assertEquals(0, EvoMarketplaceVersionChecker.compareSemanticVersions("1.0.0+one", "1.0.0+two"))
    }

    @Test
    fun `marketplace XML returns only matching plugin versions`() {
        val xml = """
            <plugin-repository><category name="Robotics">
              <idea-plugin><id>other.plugin</id><version>9.0.0</version></idea-plugin>
              <idea-plugin><id>com.inspyresoftworks.ti84evo</id><version>0.3.0</version></idea-plugin>
              <idea-plugin><id>com.inspyresoftworks.ti84evo</id><version>0.2.9</version></idea-plugin>
            </category></plugin-repository>
        """.trimIndent().toByteArray()

        assertEquals(
            listOf("0.3.0", "0.2.9"),
            EvoMarketplaceVersionChecker.parseMarketplaceVersions(xml, "com.inspyresoftworks.ti84evo"),
        )
    }

    private fun resolve(
        installed: String,
        claimed: String,
        installedHash: String = "market",
    ): EvoVersionStatus = EvoMarketplaceVersionChecker.resolve(
        installedVersion = installed,
        installedDescriptorVersion = installed,
        installedHash = installedHash,
        marketplace = EvoMarketplaceArtifact(claimed, claimed, "market"),
    )
}
