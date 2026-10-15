plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}
kotlin { jvmToolchain(17) }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.processTestResources { from(rootProject.file("fixtures")) }

java { withSourcesJar() }
publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }

tasks.processResources {
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE-FishSyncClient" }
    }
}

tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE-FishSyncClient" }
    }
}
