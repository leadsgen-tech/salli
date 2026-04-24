plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "lk.salli.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":parser"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // FileProvider for exporter share intents.
    implementation(libs.androidx.core.ktx)

    // DataStore — one tiny prefs file for settings like parse mode.
    implementation(libs.androidx.datastore.preferences)

    // WorkManager — model download runs as a CoroutineWorker with foreground service,
    // so the :data orchestrator needs the runtime API to enqueue/observe work.
    implementation(libs.androidx.work.runtime.ktx)

    // Robolectric-based Room tests use classic JUnit4; kotlinx-coroutines-test exposes
    // runBlocking/runTest for the suspend DAOs.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation("androidx.test:core:1.6.1")
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
