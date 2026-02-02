import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.robothanzo.werewolf"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    implementation("net.dv8tion:JDA:6.3.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("org.mongodb:mongodb-driver-sync:5.1.3")
    implementation("ch.qos.logback:logback-classic:1.5.7")
    implementation("com.github.RobotHanzo:JDAInteractions:v0.1.4")
    implementation("dev.arbjerg:lavaplayer:2.2.6")
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
            attributes["Main-Class"] = "dev.robothanzo.werewolf.WerewolfHelper"
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}