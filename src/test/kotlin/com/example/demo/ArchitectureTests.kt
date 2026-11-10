package com.example.demo

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Configuration
import org.springframework.modulith.core.ApplicationModules
import kotlin.test.assertTrue

class ArchitectureTests {
	@Test
	fun `features respect Spring Modulith boundaries`() {
		val modules = ApplicationModules.of(DemoApplication::class.java)

		assertTrue(modules.getModuleByName("events").isPresent, "The events feature must be discovered")
		modules.verify()
	}

	@Test
	fun `production code uses feature-first packages with only the layers it needs`() {
		val featurePackage = Regex("${Regex.escape(ROOT_PACKAGE)}\\.[a-z]+\\.(inbound|application|domain|outbound|configuration)(\\..+)?")
		val bootstrapClasses = setOf(DemoApplication::class.java.name, "$ROOT_PACKAGE.DemoApplicationKt")

		classes().should(object : ArchCondition<JavaClass>("reside in a lowercase feature's layer or be the application bootstrap") {
			override fun check(item: JavaClass, events: ConditionEvents) {
				val correctlyPlaced = item.name in bootstrapClasses || featurePackage.matches(item.packageName)
				events.add(SimpleConditionEvent(item, correctlyPlaced, "${item.name} resides in ${item.packageName}"))
			}
		}).check(productionClasses)
	}

	@Test
	fun `domain dependencies point inward`() {
		noClasses().that().resideInAPackage("$ROOT_PACKAGE.*.domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
				"$ROOT_PACKAGE.*.application..",
				"$ROOT_PACKAGE.*.inbound..",
				"$ROOT_PACKAGE.*.outbound..",
				"$ROOT_PACKAGE.*.configuration..",
			)
			.check(productionClasses)
	}

	@Test
	fun `application code does not depend on adapters or configuration`() {
		noClasses().that().resideInAPackage("$ROOT_PACKAGE.*.application..")
			.should().dependOnClassesThat().resideInAnyPackage(
				"$ROOT_PACKAGE.*.inbound..",
				"$ROOT_PACKAGE.*.outbound..",
				"$ROOT_PACKAGE.*.configuration..",
			)
			.check(productionClasses)
	}

	@Test
	fun `core code is independent of transport persistence and framework types`() {
		noClasses().that().resideInAnyPackage("$ROOT_PACKAGE.*.domain..", "$ROOT_PACKAGE.*.application..")
			.should().dependOnClassesThat().resideInAnyPackage(
				"org.springframework..",
				"org.jooq..",
				"org.flywaydb..",
				"org.hibernate..",
				"jakarta..",
				"javax.persistence..",
				"javax.servlet..",
				"javax.validation..",
				"java.sql..",
				"javax.sql..",
				"java.net.http..",
				"com.fasterxml.jackson..",
				"tools.jackson..",
			)
			.check(productionClasses)
	}

	@Test
	fun `framework wiring belongs in feature configuration packages`() {
		classes().that().areMetaAnnotatedWith(Configuration::class.java)
			.and().doNotHaveFullyQualifiedName(DemoApplication::class.java.name)
			.should().resideInAPackage("$ROOT_PACKAGE.*.configuration..")
			.check(productionClasses)
	}

	companion object {
		private const val ROOT_PACKAGE = "com.example.demo"
		private val productionClasses = ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(ROOT_PACKAGE)
	}
}
