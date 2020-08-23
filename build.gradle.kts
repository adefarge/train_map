import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    maven
    kotlin("jvm") version "1.4.0"
}

group = "fr.adefarge.project"
version = "0.1.0-SNAPSHOT"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.4")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
