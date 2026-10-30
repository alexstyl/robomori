pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.composables.kpm.settings") {
                useModule("com.composables:kpm-settings:0.5.1")
            }
        }
    }
    repositories {
        mavenLocal {
            content {
                includeModule("com.composables", "kpm")
                includeModule("com.composables", "kpm-settings")
                includeModule("com.composables.kpm", "com.composables.kpm.gradle.plugin")
                includeModule("com.composables.kpm.settings", "com.composables.kpm.settings.gradle.plugin")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins { id("com.composables.kpm.settings") }
rootProject.name = "AvdBuddyBuildLogic"
