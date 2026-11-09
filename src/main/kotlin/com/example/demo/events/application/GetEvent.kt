package com.example.demo.events.application

import com.example.demo.events.domain.Event
import java.util.UUID

sealed interface EventLookup {
	data class Found(val event: Event) : EventLookup
	data object NotFound : EventLookup
}

class GetEvent(
	private val events: EventQueries,
) {
	fun execute(id: UUID): EventLookup = events.findById(id)
		?.let(EventLookup::Found)
		?: EventLookup.NotFound
}
