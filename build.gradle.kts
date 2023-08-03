plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "dev.robothanzo.werewolf"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    implementation("net.dv8tion:JDA:5.0.0-beta.12")
    implementation("club.minnced:discord-webhooks:0.8.3")
    implementation("org.mongodb:mongodb-driver-sync:4.10.2")
    implementation("ch.qos.logback:logback-classic:1.4.8")
    implementation("com.github.RobotHanzo:JDAInteractions:v0.1.2")
    implementation("com.sedmelluq:lavaplayer:1.3.78")
    compileOnly("org.projectlombok:lombok:1.18.8")
    annotationProcessor("org.projectlombok:lombok:1.18.28")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }

    build {
        dependsOn(shadowJar)
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("")
    }

    jar {
        manifest {
            attributes["Main-Class"] = "dev.robothanzo.werewolf.WerewolfHelper"
        }
    }
}