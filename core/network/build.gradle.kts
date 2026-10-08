plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "cc.jaxy.anlobehub.core.network"
    compileSdk = 37

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:common"))
    api(libs.okhttp)
    api(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    api(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.hilt.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
    ksp(libs.hilt.compiler)
}
