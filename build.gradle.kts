plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

group = "pl.piomin"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    // Angel One SmartAPI GitHub Packages — enable in SM-12 when adding the SDK
    // maven {
    //     url = uri("https://maven.pkg.github.com/angelbroking-github/smartapi-java")
    //     credentials {
    //         username = System.getenv("GITHUB_ACTOR") ?: ""
    //         password = System.getenv("GITHUB_TOKEN") ?: ""
    //     }
    // }
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")

    // API documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // JSON
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Spring Batch (historical bulk pull job — SM-14)
    implementation("org.springframework.boot:spring-boot-starter-batch")

    // HTTP client (for Breeze API)
    implementation("org.apache.httpcomponents.client5:httpclient5")

    // TOTP for Angel One 2FA — hardened in SM-47
    implementation("dev.samstevens.totp:totp-spring-boot-starter:1.7.1")

    // JWT — added in SM-24 (Auth epic), uncommented in SM-33
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // WebSocket (Angel One SmartAPI feed) — hardened in SM-47
    implementation("org.java-websocket:Java-WebSocket:1.5.7")

    // Angel One SmartAPI — add jar to libs/ if GitHub package unavailable — SM-12
    // implementation(files("libs/smartapi-java-2.0.0.jar"))

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
