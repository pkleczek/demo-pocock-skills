package com.example.demo.events.application

import com.example.demo.events.domain.Event
import java.util.UUID

interface EventRepository {
	fun save(event: Event)
	fun findById(id: UUID): Event?
	fun findPublishedAfter(cursor: EventCursor?, limit: Int): List<Event>
}
