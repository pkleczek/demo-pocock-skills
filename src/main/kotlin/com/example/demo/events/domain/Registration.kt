package com.example.demo.events.domain

import java.util.UUID

@JvmInline
value class AttendeeName internal constructor(val value: String) {
	internal fun violations(): List<RegistrationViolation> =
		if (value.isBlank()) listOf(RegistrationViolation("fullName", "must not be blank")) else emptyList()
}

@JvmInline
value class EmailAddress internal constructor(val value: String) {
	companion object {
		internal fun normalize(value: String): EmailAddress = EmailAddress(value.trim().lowercase())

		private val FORMAT = Regex(
			"""[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?""",
		)
	}

	internal fun violations(): List<RegistrationViolation> = buildList {
		if (value.length > 254) {
			add(RegistrationViolation("email", "must be at most 254 characters"))
		} else if (!FORMAT.matches(value)) {
			add(RegistrationViolation("email", "must be a valid email address"))
		}
	}
}

enum class RegistrationStatus {
	CONFIRMED,
}

sealed interface RegistrationConfirmationResult {
	data class Confirmed(val registration: Registration) : RegistrationConfirmationResult
	data object EventNotPublished : RegistrationConfirmationResult
	data class Rejected(val violations: List<RegistrationViolation>) : RegistrationConfirmationResult
}

data class RegistrationViolation(val field: String, val message: String)

class Registration private constructor(
	val id: UUID,
	val eventId: UUID,
	val fullName: AttendeeName,
	val email: EmailAddress,
	val status: RegistrationStatus,
) {
	companion object {
		internal fun confirm(id: UUID, eventId: UUID, fullName: String, email: String): RegistrationConfirmationResult {
			val emailAddress = EmailAddress.normalize(email)
			val name = AttendeeName(fullName.trim())
			val violations = name.violations() + emailAddress.violations()
			if (violations.isNotEmpty()) return RegistrationConfirmationResult.Rejected(violations)
			return RegistrationConfirmationResult.Confirmed(
				Registration(id, eventId, name, emailAddress, RegistrationStatus.CONFIRMED),
			)
		}

		internal fun reconstitute(
			id: UUID,
			eventId: UUID,
			fullName: AttendeeName,
			email: EmailAddress,
			status: RegistrationStatus,
		): Registration = Registration(id, eventId, fullName, email, status)
	}
}
