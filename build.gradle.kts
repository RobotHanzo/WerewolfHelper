plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.10"
}

group = "dev.robothanzo.werewolf"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.mongodb:mongodb-spring-session:4.0.0-rc1")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Discord
    implementation("net.dv8tion:JDA:6.3.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("com.github.RobotHanzo:JDAInteractions:0.2.0")
    implementation("com.github.Mokulu:discord-oauth2-api:1.0.4")

    // JDA Audio supplements
    implementation("dev.arbjerg:lavaplayer:2.2.6")
    implementation("club.minnced:jdave-api:0.1.5")
    implementation("club.minnced:jdave-native-linux-x86-64:0.1.5")
    implementation("club.minnced:jdave-native-linux-aarch64:0.1.5")
    implementation("club.minnced:jdave-native-win-x86-64:0.1.5")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
}

tasks {
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }

    compileKotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    bootJar {
        mainClass.set("dev.robothanzo.werewolf.WerewolfApplication")
    }
}