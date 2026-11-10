package com.example.demo.events.application

import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventCreationResult
import com.example.demo.events.domain.EventRepository
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventApplicationTests {
	private val stored = mutableListOf<Event>()
	private val repository = object : EventRepository {
		override fun save(event: Event) { stored += event }
		override fun findById(id: UUID): Event? = stored.find { it.id == id }
	}
	private val queries = object : EventQueries {
		override fun findById(id: UUID): Event? = stored.find { it.id == id }
		override fun findPublishedAfter(cursor: EventCursor?, limit: Int): List<Event> = stored
			.sortedWith(compareBy<Event> { it.startAt }.thenBy { it.id })
			.filter { cursor == null || it.startAt > cursor.startAt || (it.startAt == cursor.startAt && it.id > cursor.id) }
			.take(limit)
	}
	private val application = EventApplication(repository, queries)

	@Test
	fun `publishing saves the created Event`() {
		val result = assertIs<EventCreationResult.Created>(application.publish(command()))

		assertEquals(listOf(result.event), stored)
		assertEquals("Kotlin in the Park", result.event.title.value)
	}

	@Test
	fun `a rejected Event is not persisted`() {
		assertIs<EventCreationResult.Rejected>(application.publish(command().copy(title = " ")))

		assertTrue(stored.isEmpty())
	}

	@Test
	fun `lookup returns the Event or an explicit not-found outcome`() {
		val event = assertIs<EventCreationResult.Created>(application.publish(command())).event

		assertEquals(event, assertIs<EventLookup.Found>(application.get(event.id)).event)
		assertEquals(EventLookup.NotFound, application.get(UUID(0, 99)))
	}

	@Test
	fun `listing uses a lookahead and continues after the last returned Event`() {
		val first = event(1)
		val second = event(2)
		val third = event(3)
		stored += listOf(third, first, second)

		val firstPage = application.list(2, null)
		assertEquals(listOf(first, second), firstPage.events)
		assertEquals(EventCursor(second.startAt, second.id), firstPage.nextCursor)
		val lastPage = application.list(2, firstPage.nextCursor)
		assertEquals(listOf(third), lastPage.events)
		assertNull(lastPage.nextCursor)
	}

	@Test
	fun `an exactly full page and an empty page have no continuation`() {
		val third = event(3)
		stored += listOf(event(1), event(2), third)

		assertNull(application.list(3, null).nextCursor)
		val empty = application.list(3, EventCursor(third.startAt, third.id))
		assertEquals(emptyList(), empty.events)
		assertNull(empty.nextCursor)
	}

	private fun event(id: Long): Event = assertIs<EventCreationResult.Created>(Event.publish(
		id = UUID(0, id),
		title = "Kotlin Meetup",
		description = null,
		startAt = command().startAt,
		endAt = command().endAt,
		timeZone = command().timeZone,
		attendance = command().attendance,
	)).event

	private fun command() = CreateEventCommand(
		title = "Kotlin in the Park",
		description = null,
		startAt = Instant.parse("2027-05-10T16:00:00Z"),
		endAt = Instant.parse("2027-05-10T18:00:00Z"),
		timeZone = ZoneId.of("Europe/Warsaw"),
		attendance = EventAttendance.InPerson("Test venue"),
	)
}
