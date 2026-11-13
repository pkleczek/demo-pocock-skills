package com.example.demo.events.inbound

import com.example.demo.events.application.RegistrationApplication
import com.example.demo.events.application.RegistrationResult
import com.example.demo.events.domain.RegistrationStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.net.URI

@RestController
@RequestMapping("/api/events/{eventId}/registrations")
class PublicRegistrationController(private val registrations: RegistrationApplication) {
	@PostMapping
	fun register(@PathVariable eventId: UUID, @RequestBody request: RegisterAttendeeRequest): ResponseEntity<*> =
		when (val result = registrations.register(eventId, request.fullName, request.email)) {
			is RegistrationResult.Registered -> ResponseEntity.status(HttpStatus.CREATED).body(
				RegistrationResponse(result.registration.id, result.registration.eventId, result.registration.status, result.joinUrl),
			)
			RegistrationResult.AlreadyRegistered -> {
				val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "An active Registration already exists for this email and Event")
				problem.title = "Already registered"
				ResponseEntity.of(problem).build<ProblemDetail>()
			}
			RegistrationResult.EventNotFound -> ResponseEntity.notFound().build<Void>()
			RegistrationResult.EventNotPublished -> {
				val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Registrations require a published Event")
				problem.title = "Event is not published"
				ResponseEntity.of(problem).build<ProblemDetail>()
			}
			is RegistrationResult.InvalidDetails -> {
				val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Registration details are invalid")
				problem.title = "Invalid Registration details"
				problem.setProperty("errors", result.violations.map { mapOf("field" to it.field, "message" to it.message) })
				ResponseEntity.of(problem).build<ProblemDetail>()
			}
		}
}

data class RegisterAttendeeRequest(val fullName: String, val email: String)

data class RegistrationResponse(val id: UUID, val eventId: UUID, val status: RegistrationStatus, val joinUrl: URI?)
