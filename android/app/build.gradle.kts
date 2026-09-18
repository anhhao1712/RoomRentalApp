import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use(::load)
}
val apiBaseUrl = localConfig.getProperty("API_BASE_URL", "http://10.11.4.29:8080/")
    .trim()
    .let { if (it.endsWith('/')) it else "$it/" }

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
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }
    buildFeatures { buildConfig = true; viewBinding = true }
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
}
