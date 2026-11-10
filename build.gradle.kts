plugins {
	jacoco
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.2"

val jooqCodegenSourceSet = sourceSets.create("jooqCodegen") {
	java.srcDir("src/jooqCodegen/java")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("tools.jackson.module:jackson-module-kotlin")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("com.tngtech.archunit:archunit")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	add(jooqCodegenSourceSet.implementationConfigurationName, "org.jooq:jooq-codegen")
	add(jooqCodegenSourceSet.implementationConfigurationName, "org.flywaydb:flyway-core")
	add(jooqCodegenSourceSet.implementationConfigurationName, "org.flywaydb:flyway-database-postgresql")
	add(jooqCodegenSourceSet.implementationConfigurationName, "org.testcontainers:testcontainers-postgresql")
	add(jooqCodegenSourceSet.runtimeOnlyConfigurationName, "org.postgresql:postgresql")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

jacoco {
	toolVersion = "0.8.16"
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	// A dependency on test would prevent this finalizer from running after test failures.
	classDirectories.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching {
		exclude("com/example/demo/events/outbound/jooq/**")
	})
	reports {
		html.required = true
		xml.required = false
		csv.required = false
	}
}

val generatedJooqDirectory = layout.buildDirectory.dir("generated/sources/jooq/main")
sourceSets.named("main") {
	java.srcDir(generatedJooqDirectory)
}

val generateJooq by tasks.registering(JavaExec::class) {
	group = "code generation"
	description = "Runs Flyway migrations in PostgreSQL Testcontainers and generates jOOQ sources."
	dependsOn(tasks.named(jooqCodegenSourceSet.classesTaskName))
	classpath = jooqCodegenSourceSet.runtimeClasspath
	mainClass.set("com.example.demo.codegen.GenerateJooq")
	args(
		layout.projectDirectory.dir("src/main/resources/db/migration").asFile.absolutePath,
		generatedJooqDirectory.get().asFile.absolutePath,
	)
	inputs.dir(layout.projectDirectory.dir("src/main/resources/db/migration"))
	inputs.files(jooqCodegenSourceSet.allSource)
	outputs.dir(generatedJooqDirectory)
}

tasks.named("compileKotlin") {
	dependsOn(generateJooq)
}

tasks.named("compileJava") {
	dependsOn(generateJooq)
}
