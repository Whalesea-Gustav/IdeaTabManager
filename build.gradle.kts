import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import java.io.File

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.whalesea"
version = providers.gradleProperty("pluginVersion").getOrElse("0.2.10")

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

    // Keep Kotlin's test annotations on the JUnit 5 API and align the complete
    // launcher/engine set used by the platform-independent test task below.
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    // The IntelliJ Platform plugin decorates the default `test` task with an IDE
    // sandbox and test-framework listener. These tests only cover serializable
    // state and Tortoise invocation helpers, so keep them on a plain JUnit JVM.
    // This also avoids loading the incompatible IDE listener shipped by some
    // Linux Rider archives in GitHub Actions.
    val testSourceSet = project.extensions.getByType<SourceSetContainer>().getByName("test")
    val unitTest = register<Test>("unitTest") {
        group = "verification"
        description = "Runs the plugin's platform-independent unit tests."
        useJUnitPlatform()
        dependsOn("testClasses")
        testClassesDirs = testSourceSet.output.classesDirs
        // Production classes reference IntelliJ APIs. Add only the platform
        // classpath; do not add the platform test runtime, which registers the
        // problematic LauncherSessionListener through ServiceLoader.
        classpath = testSourceSet.runtimeClasspath + configurations.getByName("intellijPlatformClasspath")
    }

    test {
        enabled = false
        dependsOn(unitTest)
    }

    named("buildSearchableOptions") {
        enabled = false
    }
}
