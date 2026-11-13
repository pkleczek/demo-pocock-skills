package com.example.demo.events.application

import com.example.demo.events.domain.Registration
import com.example.demo.events.domain.RegistrationId
import com.example.demo.events.domain.EventId
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventRepository
import com.example.demo.events.domain.RegistrationRepository
import com.example.demo.events.domain.RegistrationSaveResult
import com.example.demo.events.domain.RegistrationConfirmationResult
import com.example.demo.events.domain.RegistrationViolation
import java.util.UUID
import java.net.URI

class RegistrationApplication(
	private val registrations: RegistrationRepository,
	private val queries: RegistrationQueries,
	private val events: EventRepository,
) {
	fun register(eventId: EventId, fullName: String, email: String): RegistrationResult {
		val event = events.findById(eventId) ?: return RegistrationResult.EventNotFound
		return when (val result = event.register(RegistrationId(UUID.randomUUID()), fullName, email)) {
			is RegistrationConfirmationResult.Confirmed -> when (registrations.save(result.registration)) {
				RegistrationSaveResult.Saved -> RegistrationResult.Registered(
					result.registration, (event.attendance as? EventAttendance.Online)?.joinUrl,
				)
				RegistrationSaveResult.AlreadyRegistered -> RegistrationResult.AlreadyRegistered
			}
			RegistrationConfirmationResult.EventNotPublished -> RegistrationResult.EventNotPublished
			is RegistrationConfirmationResult.Rejected -> RegistrationResult.InvalidDetails(result.violations)
		}
	}

	fun review(eventId: EventId): RegistrationReview {
		if (events.findById(eventId) == null) return RegistrationReview.EventNotFound
		return RegistrationReview.Found(queries.findForEvent(eventId))
	}
}

sealed interface RegistrationReview {
	data class Found(val registrations: List<Registration>) : RegistrationReview
	data object EventNotFound : RegistrationReview
}

sealed interface RegistrationResult {
	data class Registered(val registration: Registration, val joinUrl: URI?) : RegistrationResult
	data object AlreadyRegistered : RegistrationResult
	data object EventNotFound : RegistrationResult
	data object EventNotPublished : RegistrationResult
	data class InvalidDetails(val violations: List<RegistrationViolation>) : RegistrationResult
}
