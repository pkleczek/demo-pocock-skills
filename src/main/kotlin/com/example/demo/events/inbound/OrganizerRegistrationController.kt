package com.example.demo.events.inbound

import com.example.demo.events.application.RegistrationApplication
import com.example.demo.events.application.RegistrationReview
import com.example.demo.events.domain.RegistrationStatus
import com.example.demo.events.domain.EventId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/organizer/events/{eventId}/registrations")
class OrganizerRegistrationController(private val registrations: RegistrationApplication) {
	@GetMapping
	fun review(@PathVariable eventId: UUID): ResponseEntity<RegistrationListResponse> =
		when (val result = registrations.review(EventId(eventId))) {
			is RegistrationReview.Found -> ResponseEntity.ok(RegistrationListResponse(
				result.registrations.map {
					OrganizerRegistrationResponse(it.id.value, it.eventId.value, it.fullName.value, it.email.value, it.status)
				},
			))
			RegistrationReview.EventNotFound -> ResponseEntity.notFound().build()
		}
}

data class RegistrationListResponse(val items: List<OrganizerRegistrationResponse>)

data class OrganizerRegistrationResponse(
	val id: UUID,
	val eventId: UUID,
	val fullName: String,
	val email: String,
	val status: RegistrationStatus,
)
