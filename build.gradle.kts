import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.bundling.Jar
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
val pluginDistributionDirectory = layout.buildDirectory.dir("distributions")
val currentPluginZipName = "${rootProject.name}-${project.version}.zip"
val marketplaceChangeNotes = run {
    val lines = layout.projectDirectory.file("CHANGELOG.md").asFile.readLines()
    val heading = "## $canonicalVersion"
    val start = lines.indexOf(heading)
    require(start >= 0) { "CHANGELOG.md must contain a $heading section for Marketplace update notes" }
    val bullets = lines.drop(start + 1)
        .takeWhile { !it.startsWith("## ") }
        .filter { it.startsWith("- ") }
        .map { line ->
            line.removePrefix("- ")
                .replace("**", "")
                .replace("`", "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }
    require(bullets.isNotEmpty()) { "$heading must contain at least one update note" }
    bullets.joinToString(separator = "", prefix = "<ul>", postfix = "</ul>") { "<li>$it</li>" }
}

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
            fileTree(pluginDistributionDirectory.get().asFile) {
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
    implementation(kotlin("stdlib"))
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

val cliSourceSet = sourceSets.create("cli") {
    kotlin.srcDir("src/cli/kotlin")
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val cliJar = tasks.register<Jar>("cliJar") {
    group = "distribution"
    description = "Builds the standalone PowerShell/Explorer TI-84 Evo sender"
    archiveFileName.set("ti84-evo-cli.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Main-Class"] = "com.inspyresoftworks.ti84evo.cli.EvoCli"
    from(cliSourceSet.output)
    from(sourceSets.main.get().output) {
        include("com/inspyresoftworks/ti84evo/model/**")
        include("com/inspyresoftworks/ti84evo/project/**")
        include("com/inspyresoftworks/ti84evo/protocol/**")
        include("com/inspyresoftworks/ti84evo/transport/**")
    }
    from(
        configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("kotlin-stdlib") || it.name.startsWith("jSerialComm") }
            .map(::zipTree),
    )
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}

tasks.register<Zip>("cliDistZip") {
    group = "distribution"
    description = "Packages the standalone sender and PowerShell launcher"
    dependsOn(cliJar)
    archiveFileName.set("ti84-evo-cli-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(cliJar)
    from("src/cli/scripts/ti84-evo.ps1")
}

kotlin {
    jvmToolchain(25)
<<<<<<< Updated upstream
=======
    compilerOptions {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

tasks.processResources {
    from(layout.projectDirectory.file("VERSION")) {
        into("META-INF")
        rename { "ti84-evo-version.txt" }
    }
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
        rename { "ti84-evo-license.txt" }
    }
>>>>>>> Stashed changes
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
        changeNotes = marketplaceChangeNotes
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN").filter(String::isNotBlank)
        privateKey = providers.environmentVariable("PRIVATE_KEY").filter(String::isNotBlank)
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD").filter(String::isNotBlank)
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN").filter(String::isNotBlank)
        channels = listOf(
            canonicalVersion.substringAfter('-', "").substringBefore('.').ifEmpty { "default" },
        )
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    named<BuildPluginTask>("buildPlugin") {
        dependsOn(cleanStalePluginZips)
        destinationDirectory.set(pluginDistributionDirectory)
        archiveFileName.set(currentPluginZipName)
    }
}
