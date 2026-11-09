package com.example.demo.events.application

import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventRepository
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventCreationResult
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class CreateEventCommand(
	val title: String,
	val description: String?,
	val startAt: Instant,
	val endAt: Instant,
	val timeZone: ZoneId,
	val attendance: EventAttendance,
)

class CreateEvent(
	private val events: EventRepository,
) {
	fun execute(command: CreateEventCommand): EventCreationResult {
		val result = Event.publish(
			id = UUID.randomUUID(),
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
}
