plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}

android {
    namespace = "com.solutions.alphil.zambiajobalerts"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.solutions.alphil.zambiajobalerts"
        minSdk = 28
        targetSdk = 36
        versionCode = 11
        versionName = "1.2.11.04"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.volley)
    implementation(libs.glide)
    implementation (libs.lifecycle.viewmodel)
    implementation (libs.lifecycle.livedata)
    implementation(libs.swiperefreshlayout)
    implementation(libs.play.services.ads)
    implementation(libs.okhttp)
    implementation(libs.review)
    implementation(libs.review.ktx)
    implementation(libs.installreferrer)

    implementation (platform(libs.firebase.bom))
    implementation (libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)


    // Retrofit for WP API
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)

    // Other
    implementation(libs.work.runtime)
    implementation(libs.guava)
    implementation(libs.concurrent.futures)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)
}
