package com.example.demo.events.inbound

import com.example.demo.events.application.CreateEvent
import com.example.demo.events.application.CreateEventCommand
import com.example.demo.events.domain.AttendanceMode
import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventCreationResult
import com.example.demo.events.domain.EventViolation
import com.example.demo.events.domain.EventStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RestController
@RequestMapping("/api/organizer/events")
class OrganizerEventController(
	private val createEvent: CreateEvent,
) {
	@PostMapping
	fun create(@RequestBody request: CreateEventRequest): ResponseEntity<*> = when (val mapping = request.toCommand()) {
		is CreateEventRequestMapping.Invalid -> invalidRequest(mapping.violations)
		is CreateEventRequestMapping.Valid -> {
			when (val result = createEvent.execute(mapping.command)) {
				is EventCreationResult.Created -> ResponseEntity
					.created(URI.create("/api/events/${result.event.id}"))
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.body(EventResponse.from(result.event))
				is EventCreationResult.Rejected -> invalidRequest(result.violations)
			}
		}
	}

	private fun invalidRequest(violations: List<EventViolation>): ResponseEntity<ProblemDetail> {
		val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Event details are invalid")
		problem.title = "Invalid Event details"
		problem.setProperty(
			"errors",
			violations.map { mapOf("field" to it.field, "message" to it.message) },
		)
		return ResponseEntity.badRequest()
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problem)
	}
}

data class CreateEventRequest(
	val title: String,
	val startAt: String,
	val endAt: String,
	val timeZone: String,
	val attendanceMode: AttendanceMode,
	val location: String? = null,
	val joinUrl: String? = null,
	val description: String? = null,
) {
	fun toCommand(): CreateEventRequestMapping {
		val violations = mutableListOf<EventViolation>()
		val parsedStartAt = parseUtcTimestamp(startAt, "startAt", violations)
		val parsedEndAt = parseUtcTimestamp(endAt, "endAt", violations)
		val parsedTimeZone = parseTimeZone(timeZone, violations)
		if (violations.isNotEmpty()) {
			return CreateEventRequestMapping.Invalid(violations)
		}

		val attendance = when (attendanceMode) {
			AttendanceMode.IN_PERSON -> {
				if (joinUrl != null) {
					violations += EventViolation("joinUrl", "must be omitted for an in-person Event")
				}
				if (location == null) {
					violations += EventViolation("location", "is required for an in-person Event")
				}
				location?.let { EventAttendance.InPerson(it) }
			}
			AttendanceMode.ONLINE -> {
				if (location != null) {
					violations += EventViolation("location", "must be omitted for an online Event")
				}
				if (joinUrl == null) {
					violations += EventViolation("joinUrl", "is required for an online Event")
				}
				joinUrl?.let { value ->
					try {
						EventAttendance.Online(URI.create(value))
					} catch (_: IllegalArgumentException) {
						violations += EventViolation("joinUrl", "must be a valid absolute HTTP or HTTPS URL")
						null
					}
				}
			}
		}
		if (violations.isNotEmpty()) {
			return CreateEventRequestMapping.Invalid(violations)
		}
		return CreateEventRequestMapping.Valid(
			CreateEventCommand(
				title = title,
				description = description,
				startAt = requireNotNull(parsedStartAt),
				endAt = requireNotNull(parsedEndAt),
				timeZone = requireNotNull(parsedTimeZone),
				attendance = requireNotNull(attendance),
			),
		)
	}

	private fun parseTimeZone(value: String, violations: MutableList<EventViolation>): ZoneId? {
		if (value !in ZoneId.getAvailableZoneIds()) {
			violations += EventViolation("timeZone", "must be a named IANA time-zone ID")
			return null
		}
		return try {
			ZoneId.of(value)
		} catch (_: DateTimeException) {
			violations += EventViolation("timeZone", "must be a recognized time-zone ID")
			null
		}
	}

	private fun parseUtcTimestamp(
		value: String,
		field: String,
		violations: MutableList<EventViolation>,
	): Instant? {
		val parsed = try {
			OffsetDateTime.parse(value)
		} catch (_: DateTimeException) {
			violations += EventViolation(field, "must be a valid UTC timestamp")
			return null
		}
		if (parsed.offset != ZoneOffset.UTC) {
			violations += EventViolation(field, "must use UTC (Z or +00:00)")
			return null
		}
		// PostgreSQL stores microseconds; reject values it would silently round.
		if (parsed.nano % 1_000 != 0) {
			violations += EventViolation(field, "must use at most microsecond precision")
			return null
		}
		return parsed.toInstant()
	}
}

sealed interface CreateEventRequestMapping {
	data class Valid(val command: CreateEventCommand) : CreateEventRequestMapping
	data class Invalid(val violations: List<EventViolation>) : CreateEventRequestMapping
}

data class EventResponse(
	val id: UUID,
	val title: String,
	val description: String?,
	val startAt: String,
	val endAt: String,
	val timeZone: String,
	val attendanceMode: String,
	val location: String?,
	val status: EventStatus,
) {
	companion object {
		fun from(event: Event) = EventResponse(
			id = event.id,
			title = event.title.value,
			description = event.description,
			startAt = event.startAt.toString(),
			endAt = event.endAt.toString(),
			timeZone = event.timeZone.id,
			attendanceMode = event.attendanceMode.name,
			location = event.location,
			status = event.status,
		)
	}
}
