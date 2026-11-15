plugins {
    id("com.android.library")
}

val gl19AudioTest = providers.gradleProperty("diplay.gl19AudioTest").getOrElse("baseline")
val gl19Release = providers.gradleProperty("diplay.gl19Release").orNull == "true"

// The Dalvik-era smoke test needs Robolectric 4.12.2, the newest release that still ships the
// Android 4.4 (API 19) framework jar — 4.13 removed it. It is opt-in because it also replaces the
// test source set, so the default suite keeps compiling against the current Robolectric:
//   ./gradlew :legacy-common:testDebugUnitTest -Pdiplay.api19Smoke=true
val api19Smoke = providers.gradleProperty("diplay.api19Smoke").orNull == "true"
require(gl19AudioTest in setOf("baseline", "a", "b", "aopus")) { "diplay.gl19AudioTest must be baseline, a, b or aopus" }
require(gl19AudioTest == "baseline" || providers.gradleProperty("diplay.gl19Diagnostics").orNull == "true") {
    "GL19 audio tests require -Pdiplay.gl19Diagnostics=true"
}

android {
    namespace = "com.shilapi.xcertplay.host"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 18
        buildConfigField("String", "GL19_AUDIO_TEST", "\"$gl19AudioTest\"")
        buildConfigField("boolean", "GL19_RELEASE", gl19Release.toString())
    }
    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    val testJavaDirs = if (api19Smoke) listOf("src/api19Test/java") else listOf("src/test/java")
    sourceSets.getByName("test").java.setSrcDirs(testJavaDirs)
    sourceSets.getByName("test").kotlin.setSrcDirs(testJavaDirs)
}

// Robolectric 4.12.2 bundles an ASM that cannot read the JDK 25 bytecode the daemon runs on;
// the API-19 smoke test therefore forks its test JVM on JDK 17.
if (api19Smoke) {
    tasks.withType<Test>().configureEach {
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }
}

dependencies {
    api(project(":legacy-shared"))
    implementation("androidx.activity:activity:1.2.0")
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    testImplementation("junit:junit:4.13.2")
    // 4.13 dropped the API 19 (4.4) framework jar, so the Dalvik-era smoke test pins 4.12.2.
    testImplementation("org.robolectric:robolectric:" + if (api19Smoke) "4.12.2" else "4.17")
}
