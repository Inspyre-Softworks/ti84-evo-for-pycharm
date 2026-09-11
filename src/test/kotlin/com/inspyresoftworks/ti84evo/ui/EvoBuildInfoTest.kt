package com.inspyresoftworks.ti84evo.ui

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EvoBuildInfoTest {
    @Test
    fun packagedVersionMatchesRelease() {
        val releaseVersion = Files.readString(Path.of("VERSION")).trim()
        assertEquals(releaseVersion, EvoBuildInfo.version)
    }

    @Test
    fun resolvesInstallationDirectoryFromPackagedMetadata(@TempDir tempDirectory: Path) {
        val pluginDirectory = Files.createDirectory(tempDirectory.resolve("plugin root"))
        val libDirectory = Files.createDirectory(pluginDirectory.resolve("lib"))
        val pluginJar = Files.createFile(libDirectory.resolve("ti84-evo.jar"))
        val resource = URI.create("jar:${pluginJar.toUri()}!/META-INF/ti84-evo-version.txt").toURL()

        assertEquals(
            pluginDirectory.toAbsolutePath().normalize(),
            assertNotNull(EvoBuildInfo.installationDirectoryFrom(resource)),
        )
    }
}
