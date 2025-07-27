// settings.gradle.kts
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    // Define the versions for plugins used across the project
    // This is crucial for Gradle to find the plugins themselves
    plugins {
        id("androidx.navigation.safeargs.kotlin") version "2.7.7" // Safe Args plugin version
        id("kotlin-parcelize") version "1.9.0" // Parcelize plugin version (match your Kotlin version)
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Save Status"
include(":app")
