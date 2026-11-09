package com.example.demo.events.domain

import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class AttendanceMode {
	IN_PERSON,
	ONLINE,
}

enum class EventStatus {
	PUBLISHED,
	CANCELLED,
}

data class EventViolation(
	val field: String,
	val message: String,
)

sealed interface EventCreationResult {
	data class Created(val event: Event) : EventCreationResult
	data class Rejected(val violations: List<EventViolation>) : EventCreationResult
}

sealed interface EventAttendance {
	val mode: AttendanceMode

	data class InPerson(val location: String) : EventAttendance {
		override val mode = AttendanceMode.IN_PERSON
	}

	data class Online(val joinUrl: URI) : EventAttendance {
		override val mode = AttendanceMode.ONLINE
	}
}

class Event private constructor(
	val id: UUID,
	val title: EventTitle,
	val description: String?,
	val startAt: Instant,
	val endAt: Instant,
	val timeZone: ZoneId,
	val attendance: EventAttendance,
	val status: EventStatus,
) {
	val attendanceMode: AttendanceMode get() = attendance.mode
	val location: String? get() = (attendance as? EventAttendance.InPerson)?.location

	companion object {
		fun publish(
			id: UUID,
			title: String,
			description: String?,
			startAt: Instant,
			endAt: Instant,
			timeZone: ZoneId,
			attendance: EventAttendance,
		): EventCreationResult {
			val violations = buildList {
				if (!endAt.isAfter(startAt)) add(EventViolation("endAt", "must be after startAt"))
				when (attendance) {
					is EventAttendance.InPerson -> if (attendance.location.isBlank()) {
						add(EventViolation("location", "must not be blank"))
					}
					is EventAttendance.Online -> if (!attendance.joinUrl.isValidJoinUrl()) {
						add(EventViolation("joinUrl", "must be an absolute HTTP or HTTPS URL"))
					}
				}
			}

			return when (val result = EventTitle.create(title)) {
				is EventTitleCreationResult.Rejected -> EventCreationResult.Rejected(result.violations + violations)
				is EventTitleCreationResult.Created -> if (violations.isNotEmpty()) {
					EventCreationResult.Rejected(violations)
				} else {
					EventCreationResult.Created(
						Event(id, result.title, description, startAt, endAt, timeZone, attendance, EventStatus.PUBLISHED),
					)
				}
			}
		}

		internal fun reconstitute(
			id: UUID,
			title: EventTitle,
			description: String?,
			startAt: Instant,
			endAt: Instant,
			timeZone: ZoneId,
			attendance: EventAttendance,
			status: EventStatus,
		): Event = Event(id, title, description, startAt, endAt, timeZone, attendance, status)
	}
}

private fun URI.isValidJoinUrl(): Boolean =
	isAbsolute && host != null && scheme.lowercase() in setOf("http", "https")
