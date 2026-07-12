dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")

    implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    implementation("software.amazon.awssdk:secretsmanager")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:dynamodb-enhanced")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    implementation("org.mapstruct:mapstruct:${property("mapstructVersion")}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${property("mapstructVersion")}")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:localstack")
}

// Excluded: pure Spring wiring/config, plain DTOs/entities/enums/events, plain-record client
// DTOs, and MapStruct-generated mapper code -- forcing coverage onto bean-wiring produces
// tests with no real value. Everything else (services, security, controllers, kafka, metrics,
// the AiClient/TaxpayerClient HTTP wrappers) is in scope.
val jacocoExclusions = listOf(
    "com/turbotax/refund/RefundServiceApplication*",
    "com/turbotax/refund/config/**",
    "com/turbotax/refund/domain/dto/**",
    "com/turbotax/refund/domain/enums/**",
    "com/turbotax/refund/domain/event/**",
    "com/turbotax/refund/mapper/**",
    "com/turbotax/refund/client/GuidanceDoc*",
    "com/turbotax/refund/client/GuidanceResponse*",
    "com/turbotax/refund/client/PredictionRequest*",
    "com/turbotax/refund/client/RefundPrediction*",
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
