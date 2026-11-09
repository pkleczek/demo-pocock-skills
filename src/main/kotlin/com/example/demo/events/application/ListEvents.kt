package com.example.demo.events.application

import com.example.demo.events.domain.Event
import java.time.Instant
import java.util.UUID

data class EventCursor(
	val startAt: Instant,
	val id: UUID,
)

data class EventPage(
	val events: List<Event>,
	val nextCursor: EventCursor?,
)

class ListEvents(
	private val events: EventQueries,
) {
	fun execute(limit: Int, cursor: EventCursor?): EventPage {
		val fetched = events.findPublishedAfter(cursor, limit + 1)
		val page = fetched.take(limit)
		val nextCursor = if (fetched.size > limit) {
			page.lastOrNull()?.let { EventCursor(it.startAt, it.id) }
		} else {
			null
		}
		return EventPage(page, nextCursor)
	}
}
