package com.example.demo.events.inbound

import com.fasterxml.jackson.annotation.JsonFormat
import com.example.demo.events.application.CreateEvent
import com.example.demo.events.application.CreateEventCommand
import com.example.demo.events.domain.AttendanceMode
import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventCreationResult
import com.example.demo.events.domain.EventViolation
import com.example.demo.events.domain.EventStatus
import jakarta.validation.Valid
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

@RestController
@RequestMapping("/api/organizer/events")
class OrganizerEventController(
	private val createEvent: CreateEvent,
) {
	@PostMapping
	fun create(@Valid @RequestBody request: CreateEventRequest): ResponseEntity<*> =
		when (val result = createEvent.execute(request.toCommand())) {
			is EventCreationResult.Created -> ResponseEntity
				.created(URI.create("/api/events/${result.event.id}"))
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body(EventResponse.from(result.event))
			is EventCreationResult.Rejected -> invalidRequest(result.violations)
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

@ValidEventAttendance
data class CreateEventRequest(
	val title: String,
	@field:EventTimestamp
	@field:JsonFormat(without = [JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE])
	val startAt: OffsetDateTime,
	@field:EventTimestamp
	@field:JsonFormat(without = [JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE])
	val endAt: OffsetDateTime,
	@field:NamedTimeZone
	val timeZone: String,
	val attendanceMode: AttendanceMode,
	val location: String? = null,
	val joinUrl: URI? = null,
	val description: String? = null,
) {
	fun toCommand() = CreateEventCommand(
		title = title,
		description = description,
		startAt = startAt.toInstant(),
		endAt = endAt.toInstant(),
		timeZone = ZoneId.of(timeZone),
		attendance = when (attendanceMode) {
			AttendanceMode.IN_PERSON -> EventAttendance.InPerson(requireNotNull(location))
			AttendanceMode.ONLINE -> EventAttendance.Online(requireNotNull(joinUrl))
		},
	)
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
