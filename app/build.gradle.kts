plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "lk.salli.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "lk.salli.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
        manifestPlaceholders["salliAppLabel"] = "@string/app_name"
    }

    buildTypes {
        debug {
            // Optional isolated install for visual QA on a daily-use phone. It has its
            // own database, preferences and permissions; the owner's app is untouched.
            if (providers.gradleProperty("salliPreview").orNull == "true") {
                applicationIdSuffix = ".preview"
                manifestPlaceholders["salliAppLabel"] = "Salli Preview"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":parser"))
    implementation(project(":data"))
    implementation(project(":design"))

    // Needed so AppModule can Room.databaseBuilder(...) the SalliDatabase directly.
    implementation(libs.androidx.room.runtime)

    // Coil — also referenced from app-level screens (Subscriptions) that render merchant logos.
    implementation(libs.coil.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    // App lock: ProcessLifecycleOwner tells AppLockController when Salli leaves and returns.
    implementation(libs.androidx.lifecycle.process)
    // App lock prompt (fingerprint, face or the device PIN/pattern). Pulls in androidx.fragment,
    // which MainActivity needs as a FragmentActivity.
    implementation(libs.androidx.biometric)
    // Home-screen widget.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.androidx)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.test:core:1.6.1")
}

// M3 Expressive is still annotated @ExperimentalMaterial3ExpressiveApi in material3 1.4.x, so
// the opt-in is module-wide rather than sprinkled over every call site.
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}
