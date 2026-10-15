plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "io.github.alphabs.fishsync.sample"
    compileSdk = 35
    packaging {
        // Android는 JAR 클래스패스 인덱스를 사용하지 않는다.
        resources.excludes += "META-INF/INDEX.LIST"
    }
    defaultConfig {
        applicationId = "io.github.alphabs.fishsync.sample"
        minSdk = 26
        targetSdk = 35
        versionCode =
            1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 축소 빌드도 설치해 검증할 수 있도록 테스트 앱에는 debug 서명을 사용한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility =
            JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":fishsync-android"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
