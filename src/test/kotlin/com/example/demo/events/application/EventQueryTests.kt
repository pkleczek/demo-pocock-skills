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
import kotlin.test.assertNull

class EventQueryTests {
	private val first = event("00000000-0000-0000-0000-000000000001")
	private val second = event("00000000-0000-0000-0000-000000000002")
	private val third = event("00000000-0000-0000-0000-000000000003")
	private val queries = InMemoryEventQueries(listOf(first, second, third))

	@Test
	fun `lookup returns the Event or an explicit not-found outcome`() {
		val get = GetEvent(queries)

		assertEquals(first, assertIs<EventLookup.Found>(get.execute(first.id)).event)
		assertEquals(EventLookup.NotFound, get.execute(UUID(0, 99)))
	}

	@Test
	fun `listing uses a lookahead and continues after the last returned Event`() {
		val list = ListEvents(queries)

		val firstPage = list.execute(2, null)
		assertEquals(listOf(first, second), firstPage.events)
		assertEquals(EventCursor(second.startAt, second.id), firstPage.nextCursor)
		val lastPage = list.execute(2, firstPage.nextCursor)
		assertEquals(listOf(third), lastPage.events)
		assertNull(lastPage.nextCursor)
	}

	@Test
	fun `an exactly full page and an empty page have no continuation`() {
		val list = ListEvents(queries)

		assertNull(list.execute(3, null).nextCursor)
		val empty = list.execute(3, EventCursor(third.startAt, third.id))
		assertEquals(emptyList(), empty.events)
		assertNull(empty.nextCursor)
	}

	private fun event(id: String): Event = assertIs<EventCreationResult.Created>(Event.publish(
		id = UUID.fromString(id),
		title = "Kotlin Meetup",
		description = null,
		startAt = Instant.parse("2027-05-10T16:00:00Z"),
		endAt = Instant.parse("2027-05-10T18:00:00Z"),
		timeZone = ZoneId.of("Europe/Warsaw"),
		attendance = EventAttendance.InPerson("Test venue"),
	)).event

	private class InMemoryEventQueries(private val events: List<Event>) : EventQueries {
		override fun findById(id: UUID): Event? = events.find { it.id == id }

		override fun findPublishedAfter(cursor: EventCursor?, limit: Int): List<Event> = events
			.filter { cursor == null || it.startAt > cursor.startAt || (it.startAt == cursor.startAt && it.id > cursor.id) }
			.take(limit)
	}
}
