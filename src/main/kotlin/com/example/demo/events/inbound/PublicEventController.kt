package com.example.demo.events.inbound

import com.example.demo.events.application.EventCursor
import com.example.demo.events.application.EventLookup
import com.example.demo.events.application.GetEvent
import com.example.demo.events.application.ListEvents
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DateTimeException
import java.time.Instant
import java.util.Base64
import java.util.UUID

@RestController
@RequestMapping("/api/events")
class PublicEventController(
	private val getEvent: GetEvent,
	private val listEvents: ListEvents,
) {
	@GetMapping
	fun list(
		@RequestParam(name = "limit", defaultValue = DEFAULT_PAGE_SIZE.toString()) limit: Int,
		@RequestParam(name = "cursor", required = false) cursor: String?,
	): ResponseEntity<*> {
		if (limit !in 1..MAX_PAGE_SIZE) {
			return invalidRequest("limit", "must be between 1 and $MAX_PAGE_SIZE")
		}
		val decodedCursor = when (val decoded = cursor?.let(EventCursorCodec::decode)) {
			is CursorDecodeResult.Valid -> decoded.cursor
			CursorDecodeResult.Invalid -> return invalidRequest("cursor", "must be a valid cursor")
			null -> null
		}
		val page = listEvents.execute(limit, decodedCursor)
		return ResponseEntity.ok(
			EventListResponse(
				items = page.events.map(EventResponse::from),
				nextCursor = page.nextCursor?.let(EventCursorCodec::encode),
			),
		)
	}

	private fun invalidRequest(field: String, message: String): ResponseEntity<ProblemDetail> {
		val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Event list parameters are invalid")
		problem.title = "Invalid Event list request"
		problem.setProperty("errors", listOf(mapOf("field" to field, "message" to message)))
		return ResponseEntity.badRequest()
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problem)
	}

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<EventResponse> = when (val result = getEvent.execute(id)) {
		is EventLookup.Found -> ResponseEntity.ok(EventResponse.from(result.event))
		EventLookup.NotFound -> ResponseEntity.notFound().build()
	}

	private companion object {
		const val DEFAULT_PAGE_SIZE = 20
		const val MAX_PAGE_SIZE = 100
	}
}

data class EventListResponse(
	val items: List<EventResponse>,
	val nextCursor: String?,
)

private object EventCursorCodec {
	fun encode(cursor: EventCursor): String {
		val value = "${cursor.startAt}|${cursor.id}"
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.toByteArray(Charsets.UTF_8))
	}

	fun decode(cursor: String): CursorDecodeResult {
		return try {
			val value = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
			val fields = value.split('|')
			if (fields.size != 2) {
				CursorDecodeResult.Invalid
			} else {
				CursorDecodeResult.Valid(
					EventCursor(
						startAt = Instant.parse(fields[0]),
						id = UUID.fromString(fields[1]),
					),
				)
			}
		} catch (_: IllegalArgumentException) {
			CursorDecodeResult.Invalid
		} catch (_: DateTimeException) {
			CursorDecodeResult.Invalid
		}
	}
}

private sealed interface CursorDecodeResult {
	data class Valid(val cursor: EventCursor) : CursorDecodeResult
	data object Invalid : CursorDecodeResult
}
