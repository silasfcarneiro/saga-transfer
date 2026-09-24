plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))                   // <- depende do common!

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.kafka)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly(libs.postgresql)

    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.awaitility)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.named<Jar>("jar") {
    enabled = false
}