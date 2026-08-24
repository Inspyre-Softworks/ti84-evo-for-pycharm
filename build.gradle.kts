import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.bundling.Zip

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.inspyresoftworks"
val canonicalVersion = providers.fileContents(layout.projectDirectory.file("VERSION"))
    .asText
    .map(String::trim)
    .get()
require(canonicalVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?"))) {
    "VERSION must contain a semantic version, got: $canonicalVersion"
}
version = canonicalVersion
val pluginDistributionDirectory = layout.projectDirectory.dir("build/distributions")
val currentPluginZipName = "${rootProject.name}-${project.version}.zip"

// OneDrive can turn generated directories into cloud placeholders while Gradle
// is replacing them. Keep generated output local for this specific checkout
// shape, while allowing developers and CI to override the location explicitly.
val configuredBuildDirectory = providers.gradleProperty("ti84EvoBuildDir")
    .orElse(providers.environmentVariable("TI84_EVO_BUILD_DIR"))
    .orNull

if (!configuredBuildDirectory.isNullOrBlank()) {
    layout.buildDirectory.set(file(configuredBuildDirectory))
} else if (
    System.getProperty("os.name").contains("Windows", ignoreCase = true) &&
    projectDir.absolutePath.contains("\\OneDrive\\", ignoreCase = true)
) {
    System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { localAppData ->
        layout.buildDirectory.set(file("$localAppData\\ti84-evo-for-pycharm\\build"))
    }
}

val cleanStalePluginZips = tasks.register("cleanStalePluginZips") {
    doLast {
        delete(
            fileTree(pluginDistributionDirectory.asFile) {
                include("${rootProject.name}-*.zip")
                exclude(currentPluginZipName)
            }
        )
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.fazecast:jSerialComm:2.11.4")

    intellijPlatform {
        pycharm("2026.2.1")
        bundledPlugin("PythonCore")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    // Required by PyCharm's bundled JUnit5TestSessionListener at test startup.
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(25)
}

intellijPlatform {
    // Keep development builds independent from a running IDE's cached sandbox.
    sandboxContainer.set(layout.buildDirectory.dir("idea-sandbox-${project.version}"))

    pluginConfiguration {
        id = "com.inspyresoftworks.ti84evo"
        name = "TI-84 Evo"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "262"
        }

        vendor {
            name = "Inspyre-Softworks"
        }

        description = "Native TI-84 Evo integration for PyCharm."
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    named<Zip>("buildPlugin") {
        dependsOn(cleanStalePluginZips)
        destinationDirectory.set(pluginDistributionDirectory)
        archiveFileName.set(currentPluginZipName)
    }
}
