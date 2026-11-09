package com.example.demo.events.application

import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventCreationResult
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateEventTests {
	private val events = RecordingEventRepository()
	private val createEvent = CreateEvent(events)

	@Test
	fun `publishing saves the created Event`() {
		val result = assertIs<EventCreationResult.Created>(createEvent.execute(command()))

		assertEquals(listOf(result.event), events.savedEvents)
		assertEquals("Kotlin in the Park", result.event.title.value)
	}

	@Test
	fun `a rejected Event is not persisted`() {
		assertIs<EventCreationResult.Rejected>(createEvent.execute(command().copy(title = " ")))

		assertTrue(events.savedEvents.isEmpty())
	}

	private fun command() = CreateEventCommand(
		title = "Kotlin in the Park",
		description = null,
		startAt = Instant.parse("2027-05-10T16:00:00Z"),
		endAt = Instant.parse("2027-05-10T18:00:00Z"),
		timeZone = ZoneId.of("Europe/Warsaw"),
		attendance = EventAttendance.InPerson("Test venue"),
	)

	private class RecordingEventRepository : EventRepository {
		val savedEvents = mutableListOf<Event>()

		override fun save(event: Event) {
			savedEvents += event
		}

		override fun findById(id: UUID): Event? = error("Unexpected lookup while publishing an Event")

		override fun findPublishedAfter(cursor: EventCursor?, limit: Int): List<Event> =
			error("Unexpected listing while publishing an Event")
	}
}
