plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.heedar.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.heedar.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    constraints {
        // CVE-2026-102554 (GHSA-xxph-c9ww-hj94): guava 33.0.0-android arrives
        // transitively via androidx.media3 and allocates unvalidated collection
        // sizes during deserialization (CWE-400/502/770), so it ships in the APK.
        // media3 1.4.1 through 1.11.1 all pin guava 33.3.1-android, which is below
        // the 33.7.2 fix, so a constraint is the smallest remediation that does
        // not change player behaviour. Remove once media3 pins >= 33.7.2.
        implementation("com.google.guava:guava:33.7.2-android") {
            because("CVE-2026-102554 is fixed in 33.7.2; reached via media3")
        }
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.coil)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
