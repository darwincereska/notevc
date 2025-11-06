import java.time.Instant

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
    id("com.github.gmazzo.buildconfig") version "4.1.2"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "io.notevc"
version = "1.0.0"

buildConfig {
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "BUILD_TIME", "\"${Instant.now()}\"")
    packageName("io.notevc")
}

repositories {
    mavenCentral()
}

dependencies {
    val junitVersion = "5.10.0"
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

application {
    mainClass.set("io.notevc.NoteVCKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    enabled = false
}

