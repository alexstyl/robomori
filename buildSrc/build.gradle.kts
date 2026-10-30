plugins { id("com.composables.kpm") }

repositories {
    mavenCentral()
    google()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    plugins {
        create("avdBuddyTheme") {
            id = "com.avdbuddy.theme"
            implementationClass = "com.avdbuddy.themegen.AvdBuddyThemePlugin"
        }
        create("avdBuddyDevelopmentMacIcon") {
            id = "com.avdbuddy.development-mac-icon"
            implementationClass = "com.avdbuddy.development.MacDevelopmentIconPlugin"
        }
    }
}
