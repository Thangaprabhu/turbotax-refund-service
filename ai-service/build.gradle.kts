dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
}

// Excluded: pure Spring wiring, plain enums, and plain-record DTOs -- forcing coverage onto
// bean-wiring/data carriers produces tests with no real value. Everything else (prediction
// engine, guidance retrieval, controllers) is in scope.
val jacocoExclusions = listOf(
    "com/turbotax/ai/AiServiceApplication*",
    "com/turbotax/ai/domain/enums/**",
    "com/turbotax/ai/prediction/PredictionInput*",
    "com/turbotax/ai/prediction/PredictionRequest*",
    "com/turbotax/ai/prediction/RefundPrediction*",
    "com/turbotax/ai/guidance/GuidanceDoc*",
    "com/turbotax/ai/guidance/GuidanceResponse*",
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
