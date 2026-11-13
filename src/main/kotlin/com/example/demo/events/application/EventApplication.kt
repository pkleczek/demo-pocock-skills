package com.example.demo.events.application

import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventId
import com.example.demo.events.domain.EventCreationResult
import com.example.demo.events.domain.EventRepository
import java.util.UUID

class EventApplication(
	private val events: EventRepository,
	private val queries: EventQueries,
) {
	fun publish(command: CreateEventCommand): EventCreationResult {
		val result = Event.publish(
			id = EventId(UUID.randomUUID()),
			title = command.title,
			description = command.description,
			startAt = command.startAt,
			endAt = command.endAt,
			timeZone = command.timeZone,
			attendance = command.attendance,
		)
		if (result is EventCreationResult.Created) events.save(result.event)
		return result
	}

	fun get(id: EventId): EventLookup = queries.findById(id)
		?.let(EventLookup::Found)
		?: EventLookup.NotFound

	fun list(limit: Int, cursor: EventCursor?): EventPage {
		val fetched = queries.findPublishedAfter(cursor, limit + 1)
		val page = fetched.take(limit)
		val nextCursor = if (fetched.size > limit) {
			page.lastOrNull()?.let { EventCursor(it.startAt, it.id) }
		} else {
			null
		}
		return EventPage(page, nextCursor)
	}
}
