package com.example.demo.events.outbound

import com.example.demo.events.application.RegistrationQueries
import com.example.demo.events.domain.AttendeeName
import com.example.demo.events.domain.EmailAddress
import com.example.demo.events.domain.Registration
import com.example.demo.events.domain.RegistrationRepository
import com.example.demo.events.domain.RegistrationSaveResult
import com.example.demo.events.domain.RegistrationStatus
import com.example.demo.events.outbound.jooq.Tables.REGISTRATIONS
import org.jooq.DSLContext
import org.jooq.impl.DSL.inline
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JooqRegistrationRepository(private val dsl: DSLContext) : RegistrationRepository, RegistrationQueries {
	override fun save(registration: Registration): RegistrationSaveResult {
		val inserted = dsl.insertInto(REGISTRATIONS)
			.set(REGISTRATIONS.ID, registration.id)
			.set(REGISTRATIONS.EVENT_ID, registration.eventId)
			.set(REGISTRATIONS.FULL_NAME, registration.fullName.value)
			.set(REGISTRATIONS.EMAIL, registration.email.value)
			.set(REGISTRATIONS.STATUS, registration.status.name)
			.onConflict(REGISTRATIONS.EVENT_ID, REGISTRATIONS.EMAIL)
			.where(REGISTRATIONS.STATUS.eq(inline(RegistrationStatus.CONFIRMED.name)))
			.doNothing()
			.execute()
		return if (inserted == 1) RegistrationSaveResult.Saved else RegistrationSaveResult.AlreadyRegistered
	}

	override fun findForEvent(eventId: UUID): List<Registration> = dsl.selectFrom(REGISTRATIONS)
		.where(REGISTRATIONS.EVENT_ID.eq(eventId))
		.orderBy(REGISTRATIONS.CREATED_AT.asc(), REGISTRATIONS.ID.asc())
		.fetch { record ->
			Registration.reconstitute(
				id = record.id,
				eventId = record.eventId,
				fullName = AttendeeName(record.fullName),
				email = EmailAddress(record.email),
				status = RegistrationStatus.valueOf(record.status),
			)
		}
}
