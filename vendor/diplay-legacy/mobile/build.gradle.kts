plugins {
    id("com.android.application")
}

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

// Opt-in GL19 diagnostic package: retain API18 support and coexist with published repair1 APKs.
val gl19Diagnostics = providers.gradleProperty("diplay.gl19Diagnostics").orNull == "true"
val gl19AudioTest = providers.gradleProperty("diplay.gl19AudioTest").getOrElse("baseline")
require(gl19AudioTest in setOf("baseline", "a", "b", "aopus")) { "diplay.gl19AudioTest must be baseline, a, b or aopus" }
require(gl19AudioTest == "baseline" || gl19Diagnostics) { "GL19 audio tests require GL19 diagnostics" }
val gl19AudioTestEnabled = gl19AudioTest != "baseline"
// Local car-test release: preserve the installed A package/data and signing identity.
val gl19Release = providers.gradleProperty("diplay.gl19Release").orNull == "true"
require(!gl19Release || (gl19Diagnostics && gl19AudioTest == "aopus")) {
    "GK19Play release requires GL19 diagnostics and the verified aopus policy"
}

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.carlink.legacy"
        minSdk = 18
        // API 18 predates native multidex; keep the launcher installable on 4.3.
        multiDexEnabled = true
        targetSdk = 37
        versionCode = if (gl19Release) 8 else if (gl19AudioTest == "aopus") 2 else 1
        versionName = "0.1.5"
        manifestPlaceholders["diplayAppLabel"] = when (gl19AudioTest) {
            "aopus" -> "CarLink Legacy"
            "a" -> "DiPlay GL19 A 焦点测试"
            "b" -> "DiPlay GL19 B RCC测试"
            else -> if (gl19Diagnostics) "DiPlay GL19 诊断" else "@string/app_name"
        }
        if (gl19Diagnostics) {
            ndk { abiFilters += "armeabi-v7a" }
        }

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.environmentVariable("ANDROID_KEYSTORE_PATH")
                    .getOrElse("missing-release-keystore.jks"),
            )
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = if (gl19AudioTest == "aopus") ".a" else if (gl19AudioTestEnabled) ".t$gl19AudioTest" else if (gl19Diagnostics) ".diag" else ""
            versionNameSuffix = if (gl19AudioTest == "aopus") "-legacy-debug" else if (gl19AudioTestEnabled) "-legacy-$gl19AudioTest" else if (gl19Diagnostics) "-legacy-diag" else "-debug"
        }
        release {
            isDebuggable = false
            optimization {
                enable = gl19Release
            }
            if (gl19Release) {
                applicationIdSuffix = ".a"
                versionNameSuffix = "-legacy"
            }
            // A debug signing certificate does not enable the debugger. Keeping it
            // here allows this non-debuggable release to update the tested A APK.
            signingConfig = signingConfigs.getByName(if (gl19Release) "debug" else "release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":legacy-common"))
    implementation(project(":legacy-shared"))
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.multidex:multidex:2.0.1")
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Car-test packages must be standalone. Keep ordinary source/CI builds identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() > 0 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}
tasks.register("assembleStandaloneRelease") {
    group = "build"
    description = "Build a release APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleRelease")
}
