dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("org.postgresql:postgresql")

    implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    implementation("software.amazon.awssdk:secretsmanager")
    implementation("software.amazon.awssdk:kms")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    implementation("org.mapstruct:mapstruct:${property("mapstructVersion")}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${property("mapstructVersion")}")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:localstack")
}

// Excluded: pure Spring wiring/config, plain DTOs/entities/enums, and MapStruct-generated
// mapper code -- forcing coverage onto bean-wiring produces tests with no real value.
// Everything else (services, security, controllers) is in scope.
val jacocoExclusions = listOf(
    "com/turbotax/taxpayer/TaxpayerServiceApplication*",
    "com/turbotax/taxpayer/config/**",
    "com/turbotax/taxpayer/domain/dto/**",
    "com/turbotax/taxpayer/domain/entity/**",
    "com/turbotax/taxpayer/domain/enums/**",
    "com/turbotax/taxpayer/mapper/**",
)

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )
    violationRules {
        rule {
            limit {
                minimum = "0.98".toBigDecimal()
            }
        }
    }
}
