plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize") // For @Parcelize annotation
    id("androidx.navigation.safeargs.kotlin") // Correct way to apply Safe Args plugin
}

android {
    namespace = "com.kezor.localsave.savestatus" // Ensure this is consistent: 'savestatus'
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kezor.localsave.savestatus" // Ensure this is consistent: 'savestatus'
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // Google Material Design Components
    implementation("com.google.android.material:material:1.12.0")
    // Note: Removed explicit excludes here as resolutionStrategy will handle it globally.
    // If issues persist, you might need to re-add specific excludes here for very stubborn cases.

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Ensure these versions are compatible with your compileSdk 35 and targetSdk 35
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    implementation("androidx.preference:preference-ktx:1.2.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Global dependency resolution strategy to avoid duplicate classes
configurations.all {
    resolutionStrategy {
        // Force a specific version of xmlpull to avoid conflicts with xpp3
        // This tells Gradle to use 'xmlpull:xmlpull' and ignore 'xpp3:xpp3' if both are found.
        force("xmlpull:xmlpull:1.1.3.1") // Use the version that is typically standard
        // You can also explicitly exclude the problematic module if forcing doesn't work
        // exclude(group = "xpp3", module = "xpp3") // This can be uncommented if forcing isn't enough
    }
}


















//implementation("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.2")