pluginManagement {
    repositories {
        mavenLocal {
            content {
                includeModule("com.composables", "kpm")
                includeModule("com.composables", "kpm-settings")
                includeModule("com.composables.kpm", "com.composables.kpm.gradle.plugin")
                includeModule("com.composables.kpm.settings", "com.composables.kpm.settings.gradle.plugin")
            }
        }
        gradlePluginPortal(); google(); mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.composables.kpm.settings") version "0.5.1"
}
dependencyResolutionManagement { repositories { google(); mavenCentral(); maven("https://www.jetbrains.com/intellij-repository/releases") } }
rootProject.name = "AvdBuddy"
