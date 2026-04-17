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
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("com.example.kooggen.MainKt")
}

kotlin {
    jvmToolchain(17)
}
