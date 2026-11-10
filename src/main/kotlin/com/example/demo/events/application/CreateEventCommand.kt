package com.example.demo.events.application

import com.example.demo.events.domain.EventAttendance
import java.time.Instant
import java.time.ZoneId

data class CreateEventCommand(
	val title: String,
	val description: String?,
	val startAt: Instant,
	val endAt: Instant,
	val timeZone: ZoneId,
	val attendance: EventAttendance,
)
