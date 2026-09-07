package com.inspyresoftworks.ti84evo.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.JarURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal enum class EvoVersionState {
    CHECKING,
    CURRENT,
    DEVELOPMENTAL,
    OUTDATED,
    MISMATCH,
    UNAVAILABLE,
}

internal data class EvoVersionStatus(
    val installedVersion: String,
    val marketplaceVersion: String? = null,
    val state: EvoVersionState = EvoVersionState.CHECKING,
    val detail: String = "Checking JetBrains Marketplace…",
    val installedHash: String? = null,
    val marketplaceHash: String? = null,
) {
    val tag: String?
        get() = when (state) {
            EvoVersionState.DEVELOPMENTAL -> "[DEVELOPMENTAL]"
            EvoVersionState.MISMATCH -> "[VERSION MISMATCH]"
            else -> null
        }

    val displayVersion: String
        get() = listOfNotNull(installedVersion, tag).joinToString(" ")

    val footerText: String
        get() = "v$displayVersion"

    val buildDescription: String
        get() = when {
            state == EvoVersionState.DEVELOPMENTAL -> "Developmental"
            state == EvoVersionState.MISMATCH -> "Version/hash mismatch"
            installedVersion.endsWith("-SNAPSHOT", ignoreCase = true) -> "Development snapshot"
            else -> "Release"
        }

    val marketplaceDescription: String
        get() = marketplaceVersion ?: when (state) {
            EvoVersionState.CHECKING -> "Checking…"
            EvoVersionState.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }

    val tooltip: String
        get() = buildString {
            append("Installed TI-84 Evo plugin version: ").append(installedVersion)
            marketplaceVersion?.let { append("; Marketplace version: ").append(it) }
            append(". ").append(detail)
            if (installedHash != null && marketplaceHash != null) {
                append(" Installed SHA-256: ").append(installedHash.take(12))
                append("…; Marketplace SHA-256: ").append(marketplaceHash.take(12)).append('…')
            }
        }

    companion object {
        fun checking(installedVersion: String) = EvoVersionStatus(installedVersion = installedVersion)
    }
}

internal data class EvoMarketplaceArtifact(
    val claimedVersion: String,
    val embeddedVersion: String,
    val jarSha256: String,
)

/** Checks the public Marketplace claim and compares equal-version plugin JARs by SHA-256. */
internal object EvoMarketplaceVersionChecker {
    private const val MARKETPLACE_BASE = "https://plugins.jetbrains.com"
    private const val MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024
    private const val MAX_JAR_BYTES = 32 * 1024 * 1024
    private const val MAX_DESCRIPTOR_BYTES = 1024 * 1024

    fun check(installedVersion: String, pluginId: String): EvoVersionStatus = try {
        val installedJar = installedJarPath()
        val installedHash = installedJar?.let(::sha256)
        val installedDescriptorVersion = installedJar?.let { readPluginDescriptor(it)?.version }
        val artifact = fetchMarketplaceArtifact(pluginId)
        resolve(installedVersion, installedDescriptorVersion, installedHash, artifact)
    } catch (error: Exception) {
        EvoVersionStatus(
            installedVersion = installedVersion,
            state = EvoVersionState.UNAVAILABLE,
            detail = "Marketplace verification unavailable: ${error.message ?: error.javaClass.simpleName}",
        )
    }

    internal fun resolve(
        installedVersion: String,
        installedDescriptorVersion: String?,
        installedHash: String?,
        marketplace: EvoMarketplaceArtifact,
    ): EvoVersionStatus {
        val common = EvoVersionStatus(
            installedVersion = installedVersion,
            marketplaceVersion = marketplace.claimedVersion,
            installedHash = installedHash,
            marketplaceHash = marketplace.jarSha256,
        )

        if (installedDescriptorVersion != null && installedDescriptorVersion != installedVersion) {
            return common.copy(
                state = EvoVersionState.MISMATCH,
                detail = "The installed JAR embeds version $installedDescriptorVersion but reports $installedVersion.",
            )
        }
        if (marketplace.embeddedVersion != marketplace.claimedVersion) {
            return common.copy(
                state = EvoVersionState.MISMATCH,
                detail = "Marketplace claims ${marketplace.claimedVersion}, but its downloaded JAR embeds ${marketplace.embeddedVersion}.",
            )
        }

        return when (compareSemanticVersions(installedVersion, marketplace.claimedVersion)) {
            1 -> common.copy(
                state = EvoVersionState.DEVELOPMENTAL,
                detail = "Installed version is newer than the Marketplace release.",
            )
            -1 -> common.copy(
                state = EvoVersionState.OUTDATED,
                detail = "A newer Marketplace release is available.",
            )
            else -> when {
                installedHash == null -> common.copy(
                    state = EvoVersionState.CURRENT,
                    detail = "Version matches Marketplace; JAR hashing is unavailable for this development classpath.",
                )
                installedHash != marketplace.jarSha256 -> common.copy(
                    state = EvoVersionState.MISMATCH,
                    detail = "Version matches Marketplace, but the installed and Marketplace JAR hashes differ.",
                )
                else -> common.copy(
                    state = EvoVersionState.CURRENT,
                    detail = "Version and SHA-256 hash match the Marketplace artifact.",
                )
            }
        }
    }

    internal fun compareSemanticVersions(left: String, right: String): Int {
        val leftVersion = SemanticVersion.parse(left)
        val rightVersion = SemanticVersion.parse(right)
        return leftVersion.compareTo(rightVersion).coerceIn(-1, 1)
    }

    internal fun parseMarketplaceVersions(xml: ByteArray, pluginId: String): List<String> {
        val document = secureDocumentBuilderFactory().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val plugins = document.getElementsByTagName("idea-plugin")
        return buildList {
            for (index in 0 until plugins.length) {
                val plugin = plugins.item(index) as? Element ?: continue
                if (plugin.directChildText("id") == pluginId) {
                    plugin.directChildText("version")?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }

    private fun fetchMarketplaceArtifact(pluginId: String): EvoMarketplaceArtifact {
        val versionsUrl = "$MARKETPLACE_BASE/plugins/list?pluginId=${urlEncode(pluginId)}"
        val claimedVersion = parseMarketplaceVersions(download(versionsUrl), pluginId)
            .maxWithOrNull(::compareSemanticVersions)
            ?: error("Marketplace returned no public versions for $pluginId")
        val zipUrl = "$MARKETPLACE_BASE/plugin/download?pluginId=${urlEncode(pluginId)}&version=${urlEncode(claimedVersion)}"
        val downloadedJar = findPluginJar(download(zipUrl), pluginId)
            ?: error("Marketplace ZIP does not contain the $pluginId plugin JAR")
        return EvoMarketplaceArtifact(
            claimedVersion = claimedVersion,
            embeddedVersion = downloadedJar.descriptor.version
                ?: error("Marketplace plugin descriptor has no version"),
            jarSha256 = sha256(downloadedJar.bytes),
        )
    }

    private fun download(url: String): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 5_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/xml, application/zip, application/octet-stream")
        connection.setRequestProperty("User-Agent", "TI-84-Evo-Marketplace-Check/${EvoBuildInfo.version}")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Marketplace returned HTTP $status")
            if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) error("Marketplace response is unexpectedly large")
            return connection.inputStream.use { it.readLimited(MAX_DOWNLOAD_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    private data class PluginDescriptor(val id: String?, val version: String?)

    private data class DownloadedPluginJar(val bytes: ByteArray, val descriptor: PluginDescriptor)

    private fun findPluginJar(distribution: ByteArray, pluginId: String): DownloadedPluginJar? =
        ZipInputStream(ByteArrayInputStream(distribution)).use { zip ->
            var found: DownloadedPluginJar? = null
            while (found == null) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".jar", ignoreCase = true)) {
                    val bytes = zip.readLimited(MAX_JAR_BYTES)
                    val descriptor = readPluginDescriptor(bytes)
                    if (descriptor?.id == pluginId) found = DownloadedPluginJar(bytes, descriptor)
                }
                zip.closeEntry()
            }
            found
        }

    private fun readPluginDescriptor(path: Path): PluginDescriptor? =
        ZipInputStream(Files.newInputStream(path)).use(::readPluginDescriptor)

    private fun readPluginDescriptor(jar: ByteArray): PluginDescriptor? =
        ZipInputStream(ByteArrayInputStream(jar)).use(::readPluginDescriptor)

    private fun readPluginDescriptor(zip: ZipInputStream): PluginDescriptor? {
        while (true) {
            val entry = zip.nextEntry ?: return null
            if (!entry.isDirectory && entry.name == "META-INF/plugin.xml") {
                val xml = zip.readLimited(MAX_DESCRIPTOR_BYTES)
                val root = secureDocumentBuilderFactory().newDocumentBuilder()
                    .parse(ByteArrayInputStream(xml))
                    .documentElement
                return PluginDescriptor(root.directChildText("id"), root.directChildText("version"))
            }
            zip.closeEntry()
        }
    }

    private fun installedJarPath(): Path? = runCatching {
        val resource = EvoMarketplaceVersionChecker::class.java.getResource("EvoMarketplaceVersionChecker.class")
        val jarUrl = (resource?.openConnection() as? JarURLConnection)?.run {
            useCaches = false
            jarFileURL
        }
        val location = jarUrl ?: EvoMarketplaceVersionChecker::class.java.protectionDomain?.codeSource?.location
        location?.toURI()?.let(Path::of)?.takeIf { Files.isRegularFile(it) }
    }.getOrNull()

    private fun sha256(path: Path): String =
        Files.newInputStream(path).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().toHex()
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) error("Downloaded artifact exceeds the safety limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun Element.directChildText(name: String): String? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.tagName == name) return child.textContent.trim()
        }
        return null
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private data class SemanticVersion(
        val major: Long,
        val minor: Long,
        val patch: Long,
        val preRelease: List<String>,
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
            compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
            compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
            if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
            if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
            for (index in 0 until maxOf(preRelease.size, other.preRelease.size)) {
                val left = preRelease.getOrNull(index) ?: return -1
                val right = other.preRelease.getOrNull(index) ?: return 1
                val leftNumber = left.toLongOrNull()
                val rightNumber = right.toLongOrNull()
                val comparison = when {
                    leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> left.compareTo(right)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }

        companion object {
            private val pattern = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

            fun parse(value: String): SemanticVersion {
                val match = pattern.matchEntire(value) ?: error("Unsupported version format: $value")
                return SemanticVersion(
                    major = match.groupValues[1].toLong(),
                    minor = match.groupValues[2].toLong(),
                    patch = match.groupValues[3].toLong(),
                    preRelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.') ?: emptyList(),
                )
            }
        }
    }
}
