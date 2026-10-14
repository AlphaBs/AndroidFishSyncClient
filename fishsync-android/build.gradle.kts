plugins { id("com.android.library"); kotlin("android"); `maven-publish` }
android {
    publishing { singleVariant("release") { withSourcesJar() } }
    namespace = "io.github.alphabs.fishsync.android"
    compileSdk = 35
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":fishsync-client"))
    implementation("androidx.documentfile:documentfile:1.0.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

afterEvaluate { publishing { publications { create<MavenPublication>("release") { from(components["release"]) } } } }
