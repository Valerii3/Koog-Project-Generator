plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("com.example.kooggen.cli.MainKt")
}

kotlin {
    jvmToolchain(17)
}
