import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.inspyresoftworks"
version = "0.2.4-SNAPSHOT"

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
        id = "com.inspyresoftworks.ti84evo.pycharm"
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
}
