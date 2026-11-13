plugins {
    id("com.android.library")
}

val gl19AudioTest = providers.gradleProperty("diplay.gl19AudioTest").getOrElse("baseline")
val gl19Release = providers.gradleProperty("diplay.gl19Release").orNull == "true"
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
    testImplementation("org.robolectric:robolectric:4.17")
}
