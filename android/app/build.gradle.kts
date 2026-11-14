import java.util.Properties


plugins {
    alias(libs.plugins.android.application)
}

val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use(::load)
}
// -PAPI_BASE_URL can override the local emulator URL for a hosted HTTPS backend.
val apiBaseUrl = providers.gradleProperty("API_BASE_URL")
    .orElse(localConfig.getProperty("API_BASE_URL", "http://10.0.2.2:8080/"))
    .get()
    .trim()
    .let { if (it.endsWith('/')) it else "$it/" }

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "vn.edu.thuetro"
    compileSdk = 37

    defaultConfig {
        applicationId = "vn.edu.thuetro"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        // Mỗi máy tự đặt API_BASE_URL trong local.properties; file này không được đưa lên GitHub.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"$apiBaseUrl\"")
        resValue(
            "string",
            "maptiler_api_key",
            localProps.getProperty("MAPTILER_API_KEY", "")
        )
    }
    buildFeatures {
        buildConfig = true;
        viewBinding = true;
        resValues = true
    }
    // HTTP chỉ dùng cho môi trường phát triển local; bản release yêu cầu HTTPS.
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        getByName("release") {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil)
    implementation("org.maplibre.gl:android-sdk:11.11.0")
}
