plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    `maven-publish`
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":fishsync-core"))
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

java { withSourcesJar() }
publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
