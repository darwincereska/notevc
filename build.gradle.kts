plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "io.notevc"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("io.notevc.NoteVCKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.notevc.NoteVCKt"
    }
}
