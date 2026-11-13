package com.example.demo.events.application

import com.example.demo.events.domain.Event
import java.time.Instant
import com.example.demo.events.domain.EventId

data class EventCursor(
	val startAt: Instant,
	val id: EventId,
)

data class EventPage(
	val events: List<Event>,
	val nextCursor: EventCursor?,
)
