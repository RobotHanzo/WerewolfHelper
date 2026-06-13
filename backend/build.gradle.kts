import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-scalar:2.8.15")

    // Kotlin — Spring Boot 4 uses Jackson 3 (tools.jackson); register its Kotlin module so
    // request bodies and DTOs (data classes with defaults) (de)serialize correctly.
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

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

    // Spring Context Indexer for faster startup
    annotationProcessor("org.springframework:spring-context-indexer")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Embedded MongoDB so the Spring context can be verified end-to-end without a running server.
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring4x:4.33.0")
}

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    bootJar {
        mainClass.set("dev.robothanzo.werewolf.WerewolfApplicationKt")
    }

    named<BootRun>("bootRun") {
        jvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
    }
}
