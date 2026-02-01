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
    implementation("org.mongodb:mongodb-driver-sync:5.6.2")
    implementation("ch.qos.logback:logback-classic:1.5.27")
    implementation("com.github.RobotHanzo:JDAInteractions:v0.1.4")

    // Web Server Dependencies
    implementation("io.javalin:javalin:6.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("com.github.Mokulu:discord-oauth2-api:1.0.4")

    // JDA Audio supplements
    implementation("dev.arbjerg:lavaplayer:2.2.6")
    implementation("club.minnced:jdave-api:0.1.5")
    implementation("club.minnced:jdave-native-linux-x86-64:0.1.5")
    implementation("club.minnced:jdave-native-linux-aarch64:0.1.5")
    implementation("club.minnced:jdave-native-win-x86-64:0.1.5")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
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