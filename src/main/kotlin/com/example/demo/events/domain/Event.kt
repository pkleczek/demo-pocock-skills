package com.example.demo.events.domain

import java.net.URI
import java.time.Instant
import java.time.ZoneId

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
	val id: EventId,
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

	fun register(registrationId: RegistrationId, fullName: String, email: String): RegistrationConfirmationResult =
		when (status) {
			EventStatus.PUBLISHED -> Registration.confirm(registrationId, id, fullName, email)
			EventStatus.CANCELLED -> RegistrationConfirmationResult.EventNotPublished
		}

	companion object {
		fun publish(
			id: EventId,
			title: String,
			description: String?,
			startAt: Instant,
			endAt: Instant,
			timeZone: ZoneId,
			attendance: EventAttendance,
		): EventCreationResult {
				val titleViolations = buildList {
				if (title.isBlank()) add(EventViolation("title", "must not be blank"))
				if (title.length > MAX_TITLE_LENGTH) {
					add(EventViolation("title", "must be at most $MAX_TITLE_LENGTH characters"))
				}
			}
			val otherViolations = buildList {
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
			val violations = titleViolations + otherViolations

			return if (violations.isNotEmpty()) {
				EventCreationResult.Rejected(violations)
			} else {
				EventCreationResult.Created(
					Event(id, EventTitle(title), description, startAt, endAt, timeZone, attendance, EventStatus.PUBLISHED),
				)
			}
		}

		private const val MAX_TITLE_LENGTH = 200

		internal fun reconstitute(
			id: EventId,
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
