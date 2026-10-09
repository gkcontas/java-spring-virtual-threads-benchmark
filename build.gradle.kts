plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.gatling.gradle") version "3.14.3"
}

group = "com.gkcontas"
version = "1.0.0"
description = "Virtual threads against a platform thread pool, measured under load"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.awaitility:awaitility")
}

// Gatling 3.14 runs on Netty 4.2, but Spring Boot's dependency management pins
// Netty to 4.1 across *every* configuration in the project, Gatling's included.
// The simulation then dies at startup with
// NoClassDefFoundError: io/netty/channel/IoHandle - a class that only exists in
// 4.2. Forcing the version is scoped to the Gatling configurations so the
// application itself keeps the Netty that Spring Boot was tested against:
// a load generator and the service under test have no reason to share versions.
// tcnative is left alone, it has its own numbering.
configurations.matching { it.name.startsWith("gatling") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty" && !requested.name.startsWith("netty-tcnative")) {
            useVersion("4.2.1.Final")
            because("Gatling 3.14 requires Netty 4.2; the Spring Boot BOM pins 4.1")
        }
    }
}

// The Gatling plugin forks its own JVM for `gatlingRun` and uses the Gradle
// daemon's Java, not this project's toolchain - and it exposes no launcher to
// point elsewhere, only jvmArgs. On a machine whose default `java` is older
// than 21 (this one has 17 on PATH) a simulation compiled to 21 dies with
// UnsupportedClassVersionError before a single request is sent.
//
// The fix is not to force the daemon onto 21: the load generator is a separate
// program from the service it drives and needs nothing from Java 21, so it is
// compiled to a floor of 17 and runs on whatever the daemon provides. The
// application under test stays on 21, which is the whole point of it.
tasks.named<JavaCompile>("compileGatlingJava") {
    options.release = 17
}

// The simulations read their arrival rate and delay from system properties, so
// the same scenario can be pointed at both thread models without editing code.
// The plugin forks its own JVM, so -P on the Gradle command line has to be
// forwarded explicitly; the plugin's own defaults are kept by adding to the map
// rather than replacing it.
gatling {
    val forwarded = listOf(
        "bench.rate", "bench.duration", "bench.delayMs", "bench.rounds", "bench.baseUrl"
    ).filter { project.hasProperty(it) }
        .associateWith { project.property(it) as Any }
    systemProperties = systemProperties + forwarded
}

// StructuredTaskScope is a preview API in Java 21, so the flag is needed at
// compile time and at run time. The cost is real and belongs in the README: a
// jar built this way refuses to start without --enable-preview, and preview
// APIs carry no compatibility promise between feature releases - this code does
// not compile on Java 24+, where the API was redesigned. The toolchain pin to
// 21 is what keeps that from being a surprise.
tasks.withType<JavaCompile>().matching { it.name != "compileGatlingJava" }.configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "--enable-preview"))
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("--enable-preview")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

