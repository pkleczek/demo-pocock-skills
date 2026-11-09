package com.example.demo.events.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventTests {
	private val id = UUID.fromString("5319b0e7-387c-40c6-8edc-b4b11fb89c86")
	private val startAt = Instant.parse("2027-05-10T16:00:00Z")
	private val endAt = Instant.parse("2027-05-12T18:00:00Z")
	private val timeZone = ZoneId.of("Europe/Warsaw")

	@Test
	fun `an in-person Event is published immediately with its overall multi-day range`() {
		val event = assertIs<EventCreationResult.Created>(publish()).event

		assertEquals(id, event.id)
		assertEquals("Kotlin in the Park", event.title.value)
		assertEquals("An afternoon of talks", event.description)
		assertEquals(startAt, event.startAt)
		assertEquals(endAt, event.endAt)
		assertEquals(timeZone, event.timeZone)
		assertEquals(EventStatus.PUBLISHED, event.status)
		assertEquals(AttendanceMode.IN_PERSON, event.attendanceMode)
		assertEquals("Test venue", event.location)
	}

	@ParameterizedTest
	@ValueSource(strings = ["http://meet.example.test/room", "https://meet.example.test/room"])
	fun `an online Event accepts an absolute HTTP or HTTPS join URL`(url: String) {
		val attendance = EventAttendance.Online(URI.create(url))
		val event = assertIs<EventCreationResult.Created>(publish(attendance = attendance)).event

		assertEquals(EventStatus.PUBLISHED, event.status)
		assertEquals(AttendanceMode.ONLINE, event.attendanceMode)
		assertEquals(attendance, event.attendance)
		assertEquals(null, event.location)
	}

	@ParameterizedTest
	@ValueSource(longs = [0, -1])
	fun `an Event must end after it starts`(durationSeconds: Long) {
		val result = assertIs<EventCreationResult.Rejected>(publish(endAt = startAt.plusSeconds(durationSeconds)))

		assertEquals(listOf(EventViolation("endAt", "must be after startAt")), result.violations)
	}

	@ParameterizedTest
	@ValueSource(strings = ["", " ", "\t\n"])
	fun `an in-person Event requires a nonblank location`(location: String) {
		val result = assertIs<EventCreationResult.Rejected>(publish(attendance = EventAttendance.InPerson(location)))

		assertEquals(listOf(EventViolation("location", "must not be blank")), result.violations)
	}

	@ParameterizedTest
	@ValueSource(strings = ["", "/room", "ftp://meet.example.test/room", "https:/room", "mailto:organizer@example.test"])
	fun `an online Event rejects join URLs without an HTTP or HTTPS scheme and host`(url: String) {
		val result = assertIs<EventCreationResult.Rejected>(publish(attendance = EventAttendance.Online(URI.create(url))))

		assertEquals(
			listOf(EventViolation("joinUrl", "must be an absolute HTTP or HTTPS URL")),
			result.violations,
		)
	}

	@Test
	fun `publishing reports title and other Event violations together`() {
		val result = assertIs<EventCreationResult.Rejected>(
			publish(title = " ", endAt = startAt, attendance = EventAttendance.InPerson(" ")),
		)

		assertEquals(
			listOf(
				EventViolation("title", "must not be blank"),
				EventViolation("endAt", "must be after startAt"),
				EventViolation("location", "must not be blank"),
			),
			result.violations,
		)
	}

	private fun publish(
		title: String = "Kotlin in the Park",
		endAt: Instant = this.endAt,
		attendance: EventAttendance = EventAttendance.InPerson("Test venue"),
	): EventCreationResult = Event.publish(
		id = id,
		title = title,
		description = "An afternoon of talks",
		startAt = startAt,
		endAt = endAt,
		timeZone = timeZone,
		attendance = attendance,
	)
}
