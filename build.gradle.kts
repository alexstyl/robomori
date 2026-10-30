import dev.nucleusframework.desktop.application.dsl.TargetFormat
import dev.nucleusframework.desktop.application.dsl.PublishMode
import dev.nucleusframework.desktop.application.tasks.AbstractJPackageTask
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
plugins {
    id("com.composables.kpm")
}
val buildVersion = project.version.toString()
val macBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orElse("1").get()
require(buildVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Set a stable version in package.yml" }
val scrcpyVersion = "5.0.1"
val hostOs = System.getProperty("os.name")
val hostArch = System.getProperty("os.arch")
val scrcpyAsset = when {
    hostOs.startsWith("Mac") && hostArch in setOf("aarch64", "arm64") ->
        "macos-aarch64" to "33611e51977a8289e2e124b220f727895181ef2eb9060e0987a4dc2db9cf0d6b"
    hostOs.startsWith("Mac") && hostArch == "x86_64" ->
        "macos-x86_64" to "31a5467a9e907f162b093267cbbab1276b15c6e709c2ea8159566a8d165beb50"
    hostOs.startsWith("Windows") && hostArch in setOf("aarch64", "arm64") ->
        "winarm64" to "16c3fc2068df64946f670e3504b4752073ebae47a416a45b46e79a5ab19c689b"
    hostOs.startsWith("Windows") && hostArch in setOf("amd64", "x86_64") ->
        "win64" to "b12a2c4ee8be317422451fc7dcf8ee20a71b5ea7ef9ad73ddd825a25316227a5"
    hostOs.startsWith("Linux") && hostArch in setOf("amd64", "x86_64") ->
        "linux-x86_64" to "9f969d30cc574816077edecda65719c1c70b0aee1df1ba5fa00779ac07b8fd6e"
    else -> error("No bundled scrcpy release for $hostOs/$hostArch")
}
val scrcpyName = "scrcpy-${scrcpyAsset.first}-v$scrcpyVersion"
val scrcpyArchive = layout.buildDirectory.file("scrcpy/$scrcpyName.${if (scrcpyAsset.first.startsWith("win")) "zip" else "tar.gz"}")
val downloadScrcpy = tasks.register("downloadScrcpy") {
    outputs.file(scrcpyArchive)
    doLast {
        val archive = scrcpyArchive.get().asFile
        archive.parentFile.mkdirs()
        val download = archive.resolveSibling("${archive.name}.part")
        uri("https://github.com/Genymobile/scrcpy/releases/download/v$scrcpyVersion/${archive.name}")
            .toURL().openStream().use { input -> download.outputStream().use(input::copyTo) }
        val digest = MessageDigest.getInstance("SHA-256").digest(download.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == scrcpyAsset.second) {
            download.delete()
            "scrcpy archive checksum mismatch"
        }
        Files.move(download.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
val scrcpyStaging = layout.buildDirectory.dir("scrcpy/staged")
val macSigningIdentity = providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY").orElse("")
val stageScrcpy = tasks.register<Sync>("stageScrcpy") {
    dependsOn(downloadScrcpy)
    from(if (scrcpyAsset.first.startsWith("win")) zipTree(scrcpyArchive) else tarTree(resources.gzip(scrcpyArchive))) {
        include("$scrcpyName/scrcpy")
        include("$scrcpyName/scrcpy.exe")
        include("$scrcpyName/scrcpy-server")
        include("$scrcpyName/*.dll")
        include("$scrcpyName/LICENSE*")
    }
    into(scrcpyStaging)
    inputs.property("macSigningIdentity", macSigningIdentity)
    doLast {
        if (hostOs.startsWith("Mac") && macSigningIdentity.get().isNotEmpty()) {
            val executable = scrcpyStaging.get().file("$scrcpyName/scrcpy").asFile
            val signing = ProcessBuilder("/usr/bin/codesign", "--force", "--options", "runtime", "--timestamp",
                "--sign", macSigningIdentity.get(), executable.absolutePath).inheritIO().start()
            check(signing.waitFor() == 0) { "Could not sign the bundled screen recorder." }
        }
    }
}
val bundleScrcpy = tasks.register<Zip>("bundleScrcpy") {
    dependsOn(stageScrcpy)
    from(scrcpyStaging)
    archiveFileName.set("scrcpy-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("scrcpy/bundle"))
}
tasks.processResources {
    dependsOn(bundleScrcpy)
    from(bundleScrcpy.flatMap { it.archiveFile }) { into("scrcpy") }
}
tasks.processResources {
    inputs.property("buildVersion", buildVersion)
    filesMatching("avdbuddy-build.properties") {
        expand("buildVersion" to buildVersion)
    }
}

protobuf { protoc { artifact = "com.google.protobuf:protoc:3.25.9" } }
kotlin { jvmToolchain { languageVersion = JavaLanguageVersion.of(25); vendor = JvmVendorSpec.JETBRAINS } }

dependencies {
    implementation(compose.desktop.currentOs) {
        // Exclude Material from this app's UI, not from Hot Reload's separate developer tools.
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.compose.material3")
        exclude(group = "androidx.compose.material")
        exclude(group = "androidx.compose.material3")
    }
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    // Opt-in localhost checks depend on external service state, not just test inputs.
    outputs.upToDateWhen { !providers.environmentVariable("AVDBUDDY_LOCAL_TEST_KEY_FILE").isPresent }
    outputs.doNotCacheIf("Local licensing integration is enabled") {
        providers.environmentVariable("AVDBUDDY_LOCAL_TEST_KEY_FILE").isPresent
    }
    inputs.property("installedSdkSmoke", providers.environmentVariable("AVDBUDDY_TEST_INSTALLED_SDK").orElse("false"))
    inputs.property("imageInstallSmoke", providers.environmentVariable("AVDBUDDY_TEST_IMAGE_INSTALL").orElse("false"))
    useJUnitPlatform()
    maxParallelForks = 1
    systemProperty("java.awt.headless", "true")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}
nucleus {
    application {
        mainClass = "com.avdbuddy.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get().metadata.installationPath.asFile.path
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        if (System.getProperty("os.name").startsWith("Mac")) {
            jvmArgs += listOf("-Dapple.awt.application.name=Robomori", "-Dapple.awt.application.appearance=NSAppearanceNameAqua")
        }
        nativeDistributions {
            targetFormats(*when {
                System.getProperty("os.name").startsWith("Mac") -> arrayOf(TargetFormat.Dmg, TargetFormat.Zip)
                System.getProperty("os.name").startsWith("Windows") -> arrayOf(TargetFormat.Nsis)
                else -> arrayOf(TargetFormat.AppImage)
            })
            packageName = "Robomori"
            packageVersion = buildVersion
            // Packaging runs the app once to record what it loads at startup, so later launches start faster.
            // The cache only works with the JDK and platform it was built on, so each platform builds its own.
            enableAotCache = true
            windows {
                iconFile.set(project.file("packaging/app-icon.ico"))
                nsis { oneClick = false; allowToChangeInstallationDirectory = true; runAfterFinish = false }
            }
            linux {
                iconFile.set(project.file("packaging/app-icon.png"))
                packageName = "robomori"
                appCategory = "Development"
                debMaintainer = "Alex Styl"
            }
            description = "Android Virtual Device manager"
            vendor = "Alex Styl"
            modules("java.net.http", "java.prefs", "java.xml", "jdk.unsupported")
            publish {
                publishMode = PublishMode.Never
            }
            macOS {
                bundleID = "com.alexstyl.robomori"
                iconFile.set(project.file("packaging/app-icon.icns"))
                minimumSystemVersion = "12.0"
                packageBuildVersion = macBuildNumber
                signing {
                    sign.set(providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY").isPresent)
                    identity.set(providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY"))
                }
                notarization {
                    keychainProfile.set(providers.environmentVariable("AVDBUDDY_NOTARY_PROFILE"))
                    keychainPath.set(providers.environmentVariable("AVDBUDDY_NOTARY_KEYCHAIN"))
                }
            }
        }
    }
}

// jpackage rejects 0.x app versions. Use the build number for its input, then
// restore the public version and re-sign the outer bundle before packaging it.
if (System.getProperty("os.name").startsWith("Mac") && buildVersion.startsWith("0.")) {
    afterEvaluate {
        tasks.named<AbstractJPackageTask>("createDistributable") {
            packageVersion.set(macBuildNumber)
            inputs.property("publicAppVersion", buildVersion)
            doLast {
                val bundle = layout.buildDirectory.dir("compose/binaries/main/app/Robomori.app").get().asFile
                providers.exec {
                    commandLine("/usr/libexec/PlistBuddy", "-c", "Set :CFBundleShortVersionString $buildVersion", bundle.resolve("Contents/Info.plist").absolutePath)
                }.result.get().assertNormalExitValue()
                val identity = providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY")
                if (identity.isPresent) {
                    providers.exec {
                        commandLine("/usr/bin/codesign", "--force", "--timestamp", "--preserve-metadata=identifier,entitlements,requirements,flags,runtime", "--sign", identity.get(), bundle.absolutePath)
                    }.result.get().assertNormalExitValue()
                }
            }
        }
    }
}

// Nucleus signs the app bundle; also sign the disk image before notarizing it.
tasks.matching { it.name == "packageDmg" }.configureEach {
    val identity = providers.environmentVariable("AVDBUDDY_MAC_SIGN_IDENTITY")
    inputs.property("diskImageSigningIdentity", identity.orElse(""))
    doLast {
        if (identity.isPresent) {
            val images = layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile.listFiles().orEmpty().filter { it.extension == "dmg" }
            check(images.size == 1) { "Expected exactly one DMG to sign" }
            providers.exec { commandLine("/usr/bin/codesign", "--force", "--timestamp", "--sign", identity.get(), images.single().absolutePath) }.result.get().assertNormalExitValue()
        }
    }
}

// Exercise the real command-line entry point without starting Compose or opening a window.
tasks.register<JavaExec>("platformDiagnostics") {
    group = "verification"
    description = "Run application diagnostics headlessly on the current host"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.avdbuddy.MainKt")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    })
    systemProperty("java.awt.headless", "true")
    args("--diagnostics")
}
