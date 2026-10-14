plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "io.github.alphabs.fishsync.sample"
    compileSdk = 35
    defaultConfig { applicationId = "io.github.alphabs.fishsync.sample"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // This is a test app. Debug signing allows installing the minified verification build.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":fishsync-android"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
