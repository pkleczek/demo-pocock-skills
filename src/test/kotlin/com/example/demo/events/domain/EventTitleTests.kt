package com.example.demo.events.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventTitleTests {
	@Test
	fun `a valid title retains its original text`() {
		val result = assertIs<EventTitleCreationResult.Created>(EventTitle.create("Kotlin in the Park"))

		assertEquals("Kotlin in the Park", result.title.value)
	}

	@ParameterizedTest
	@ValueSource(strings = ["", " ", "\t\n"])
	fun `a title must not be blank`(text: String) {
		val result = assertIs<EventTitleCreationResult.Rejected>(EventTitle.create(text))

		assertEquals(listOf(EventViolation("title", "must not be blank")), result.violations)
	}

	@Test
	fun `a title can contain exactly 200 characters`() {
		val text = "a".repeat(200)
		val result = assertIs<EventTitleCreationResult.Created>(EventTitle.create(text))

		assertEquals(text, result.title.value)
	}

	@Test
	fun `a title cannot exceed 200 characters`() {
		val result = assertIs<EventTitleCreationResult.Rejected>(EventTitle.create("a".repeat(201)))

		assertEquals(listOf(EventViolation("title", "must be at most 200 characters")), result.violations)
	}

	@Test
	fun `titles with the same text are equal values`() {
		val first = assertIs<EventTitleCreationResult.Created>(EventTitle.create("Kotlin in the Park")).title
		val second = assertIs<EventTitleCreationResult.Created>(EventTitle.create("Kotlin in the Park")).title

		assertEquals(first, second)
		assertEquals(first.hashCode(), second.hashCode())
	}
}
