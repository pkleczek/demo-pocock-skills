package com.example.demo.events.inbound

import com.example.demo.events.application.EventCursor
import com.example.demo.events.application.EventLookup
import com.example.demo.events.application.EventApplication
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.core.convert.converter.Converter
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Base64
import java.util.UUID

@RestController
@RequestMapping("/api/events")
class PublicEventController(
	private val events: EventApplication,
) {
	@GetMapping
	fun list(
		@Min(1)
		@Max(MAX_PAGE_SIZE.toLong())
		@RequestParam(name = "limit", defaultValue = DEFAULT_PAGE_SIZE.toString()) limit: Int,
		@RequestParam(name = "cursor", required = false) cursor: EventCursor?,
	): ResponseEntity<*> {
		val page = events.list(limit, cursor)
		return ResponseEntity.ok(
			EventListResponse(
				items = page.events.map(EventResponse::from),
				nextCursor = page.nextCursor?.let(EventCursorCodec::encode),
			),
		)
	}

	@GetMapping("/{id}")
	fun get(@PathVariable id: UUID): ResponseEntity<EventResponse> = when (val result = events.get(id)) {
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

	fun decode(cursor: String): EventCursor {
		val value = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
		val fields = value.split('|')
		require(fields.size == 2) { "must be a valid cursor" }
		return EventCursor(startAt = Instant.parse(fields[0]), id = UUID.fromString(fields[1]))
	}
}

@Component
class EventCursorConverter : Converter<String, EventCursor> {
	override fun convert(source: String): EventCursor = EventCursorCodec.decode(source)
}
