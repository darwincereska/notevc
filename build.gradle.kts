import java.time.Instant

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
    id("com.github.gmazzo.buildconfig") version "4.1.2"
    id("com.gradleup.shadow") version "9.2.2"
    id("org.graalvm.buildtools.native") version "0.9.28"
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



tasks.shadowJar {
    archiveClassifier.set("")  // This should remove the -all suffix
    manifest {
        attributes(mapOf("Main-Class" to "io.notevc.NoteVCKt"))
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("notevc")
            mainClass.set("io.notevc.NoteVCKt")
            
            // Force it to use the shadowJar
            classpath.from(tasks.shadowJar.get().outputs.files)
            
            buildArgs.addAll(listOf(
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "-H:+UnlockExperimentalVMOptions",
                
                "--initialize-at-build-time=kotlin",
                "--initialize-at-build-time=kotlinx",
                "--initialize-at-build-time=io.notevc",
                
                "--enable-monitoring=heapdump,jfr",
                "-H:IncludeResources=.*\\.json",
                "-H:IncludeResources=.*\\.properties"
            ))
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.nativeCompile {
    dependsOn(tasks.shadowJar)
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
