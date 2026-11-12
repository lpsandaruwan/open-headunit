import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

include(":app", ":contract")

// The DF firmware accepts our builds under one package id. Include Link in that APK so vehicle
// telemetry and projection can coexist. Ordinary Open Headunit builds have no OABL dependency.
if (providers.gradleProperty("headunitOablLink").orNull == "true") {
    require(providers.gradleProperty("headunitVysor").orNull == "true") {
        "headunitOablLink requires -PheadunitVysor=true for the Dongfeng head unit"
    }
    val linkRoot = file(providers.gradleProperty("oablLinkDir").orNull ?: "../oabl/oabl-link")
    require(linkRoot.resolve("headunit/build.gradle").isFile) {
        "OABL Link sources missing at $linkRoot; set -PoablLinkDir=/path/to/oabl-link"
    }
    include(":oablLink", ":core", ":wire", ":bluetooth")
    project(":oablLink").projectDir = linkRoot.resolve("headunit")
    listOf("core", "wire", "bluetooth").forEach {
        project(":$it").projectDir = linkRoot.resolve(it)
    }
}


rootProject.name = "open-headunit"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://jitpack.io")
        }
    }
}
