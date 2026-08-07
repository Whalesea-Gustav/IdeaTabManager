import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.File

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.whalesea"
version = providers.gradleProperty("pluginVersion").getOrElse("0.2.4")

val riderSdkPath = providers.gradleProperty("riderSdkPath")
    .orElse(providers.provider {
        File(System.getenv("LOCALAPPDATA") ?: "", "Programs/Rider 2")
            .takeIf(File::isDirectory)
            ?.absolutePath
    })

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (riderSdkPath.isPresent) {
            // Prefer the installed IDE during local development: no duplicate multi-GB SDK download.
            local(riderSdkPath.get())
        } else {
            // CI and machines without Rider download the distribution archive, not the unsupported installer.
            rider("2026.2") {
                useInstaller = false
            }
        }
        pluginVerifier()
    }

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

intellijPlatform {
    buildSearchableOptions = false

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginChannel")
            .map { listOf(it) }
            .orElse(listOf("default"))
    }

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }

    pluginVerification {
        ides {
            riderSdkPath.orNull?.let { local(file(it)) }
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    named("buildSearchableOptions") {
        enabled = false
    }
}
