plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "0.1.0"

val ktorVersion = "3.1.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(kotlin("stdlib"))

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("com.example.kooggen.backend.MainKt")
}

kotlin {
    jvmToolchain(17)
}

// Run this task manually before building the backend jar:
//   ./gradlew :backend:buildFrontend
// Or build the frontend directly: cd frontend && npm run build
tasks.register<Exec>("buildFrontend") {
    workingDir = File(rootProject.projectDir, "frontend")
    val npmPath = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"
    commandLine(npmPath, "run", "build")
    inputs.dir(File(rootProject.projectDir, "frontend/src"))
    outputs.dir("src/main/resources/static")
}
