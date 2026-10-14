plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("android") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("com.android.library") version "8.9.2" apply false
    id("com.android.application") version "8.9.2" apply false
}
allprojects {
    group = "io.github.alphabs.fishsync"
    version = "0.1.0-SNAPSHOT"
}

val ktlint by configurations.creating

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:1.7.1:all") {
        isTransitive = false
    }
}

val kotlinStyleSources =
    listOf(
        "**/src/**/*.kt",
        "*.gradle.kts",
        "**/build.gradle.kts",
    )

tasks.register<JavaExec>("checkKotlinStyle") {
    group = "verification"
    description = "Kotlin 코드와 Gradle 스크립트의 스타일을 검사합니다."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(kotlinStyleSources)
}

tasks.register<JavaExec>("formatKotlin") {
    group = "formatting"
    description = "프로젝트 코드 스타일에 맞춰 Kotlin 코드를 정리합니다."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("--format")
    args(kotlinStyleSources)
}
