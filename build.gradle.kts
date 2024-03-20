import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.github.slimjar").version("1.3.0")
}

group = "dev.robothanzo.werewolf"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://m2.dv8tion.net/releases")
    maven("https://repo.gkpixel.com/mirror/")
}

dependencies {
    slim("net.dv8tion:JDA:5.0.0-beta.20")
    slim("club.minnced:discord-webhooks:0.8.4")
    slim("org.mongodb:mongodb-driver-sync:4.11.1")
    slim("ch.qos.logback:logback-classic:1.4.14")
    slim("com.github.RobotHanzo:JDAInteractions:0.1.2")
    implementation("io.github.slimjar:slimjar:1.2.6")
    slim("com.sedmelluq:lavaplayer:1.3.78")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }

    jar {
        manifest {
            attributes["Main-Class"] = "dev.robothanzo.werewolf.Bootstrapper"
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}