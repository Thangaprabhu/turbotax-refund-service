plugins {
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.turbotax"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

extra["awsSdkVersion"] = "2.26.27"
extra["mapstructVersion"] = "1.6.0"
extra["testcontainersVersion"] = "1.20.1"

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    configurations {
        named("compileOnly") { extendsFrom(configurations.getByName("annotationProcessor")) }
    }

    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter-actuator")
        "implementation"("io.micrometer:micrometer-registry-prometheus")
        "implementation"("io.micrometer:micrometer-tracing-bridge-otel")
        "implementation"("io.opentelemetry:opentelemetry-exporter-otlp")

        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"(platform("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}"))
        "testImplementation"("org.testcontainers:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            // Integration tests (Testcontainers: Postgres/Kafka/LocalStack) are excluded from the
            // default run -- they need infra this project doesn't auto-provision, so they're not
            // reliable enough to drive coverage/CI on their own. Run explicitly:
            // ./gradlew test -PincludeIntegration
            if (!project.hasProperty("includeIntegration")) {
                excludeTags("integration")
            }
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }
}
