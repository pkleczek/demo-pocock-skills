package com.example.demo.events.application

import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventId

interface EventQueries {
	fun findById(id: EventId): Event?
	fun findPublishedAfter(cursor: EventCursor?, limit: Int): List<Event>
}
