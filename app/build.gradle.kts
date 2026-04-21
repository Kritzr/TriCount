plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.kapt")
}

// Read the Fixer API key from local.properties (never commit that file).
// In local.properties add one line:  FIXER_API_KEY=your_key_here
val fixerKey = project.findProperty("FIXER_API_KEY") as String? ?: ""

android {
    namespace = "com.example.tricount"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tricount"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Injects BuildConfig.FIXER_API_KEY so the key never appears in source code
        buildConfigField("String", "FIXER_API_KEY", "\"$fixerKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose     = true
        buildConfig = true   // required so BuildConfig.FIXER_API_KEY is generated
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Lifecycle & Navigation
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Activity & UI
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.core:core-ktx:1.10.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Retrofit + Gson + OkHttp (used for Fixer.io currency API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.8")

    // Firebase BoM — controls ALL firebase versions, never add versions individually
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-crashlytics")

    // Google Sign-In — must stay on 20.x, version 21+ removed GoogleSignIn legacy API
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Coroutines support for Firebase Tasks (.await())
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}