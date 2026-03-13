plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

group = "com.salesforce"
version = "1.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

application {
    mainClass.set("com.salesforce.MainKt")
    applicationName = "test"
    executableDir = ""
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDir("${rootProject.projectDir}/shared/src/kotlin")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<Copy>("copyDist") {
    from(tasks.named<Sync>("installDist").map { it.destinationDir }) {
        include("test")
        include("lib/**")
    }
    into(rootProject.projectDir)
}

tasks.named("installDist") {
    finalizedBy("copyDist")
}

tasks.test {
    useJUnitPlatform()
}