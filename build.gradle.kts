import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.slimjar.task.SlimJar

plugins {
    java
//    id("com.gradleup.shadow") version "8.3.0" // not yet supported by slimjar
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("dev.racci.slimjar").version("1.6.1")
}

group = "dev.robothanzo.werewolf"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    slim("net.dv8tion:JDA:5.1.0")
    slim("club.minnced:discord-webhooks:0.8.4")
    slim("org.mongodb:mongodb-driver-sync:5.1.3")
    slim("ch.qos.logback:logback-classic:1.5.7")
    slim("com.github.RobotHanzo:JDAInteractions:0.1.2")
    implementation("com.github.RobotHanzo:slimjar:master-SNAPSHOT")
    slim("dev.arbjerg:lavaplayer:2.2.1")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    jar {
        manifest {
            attributes["Main-Class"] = "dev.robothanzo.werewolf.Bootstrapper"
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
    }

    named<SlimJar>("slimJar") {
        dependsOn(shadowJar)
    }

    build {
        dependsOn(shadowJar)
    }
}