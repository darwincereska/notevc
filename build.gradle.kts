import java.time.Instant

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
    id("com.github.gmazzo.buildconfig") version "4.1.2"
    id("com.gradleup.shadow") version "9.2.2"
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "org.notevc"
version = "1.0.6"

buildConfig {
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "BUILD_TIME", "\"${Instant.now()}\"")
    packageName("org.notevc")
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
    mainClass.set("org.notevc.NoteVCKt")
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes(mapOf("Main-Class" to "org.notevc.NoteVCKt"))
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("notevc")
            mainClass.set("org.notevc.NoteVCKt")

            buildArgs.addAll(listOf(
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "-H:+UnlockExperimentalVMOptions",
                "--initialize-at-build-time=kotlin",
                "--initialize-at-build-time=kotlinx",
                "--initialize-at-build-time=io.notevc",
                "-H:IncludeResources=.*\\.json",
                "-H:IncludeResources=.*\\.properties",
                "-Ob",  // Optimize for size
                "--gc=serial",  // Use smaller GC (if you don't need G1)
                "--strict-image-heap"
            ))
        }
    }
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

tasks.startScripts {
    dependsOn(tasks.shadowJar)
}
