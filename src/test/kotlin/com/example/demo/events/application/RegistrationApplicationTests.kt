package com.example.demo.events.application

import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventCreationResult
import com.example.demo.events.domain.EventId
import com.example.demo.events.domain.EventRepository
import com.example.demo.events.domain.Registration
import com.example.demo.events.domain.RegistrationRepository
import com.example.demo.events.domain.RegistrationSaveResult
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RegistrationApplicationTests {
	@Test
	fun `Organizer review returns the saved Registration for its Event only`() {
		val eventId = EventId(UUID.fromString("5319b0e7-387c-40c6-8edc-b4b11fb89c86"))
		val otherEventId = EventId(UUID.fromString("73cdd62f-0111-4fbe-8b82-0769afef08bd"))
		val events = listOf(event(eventId), event(otherEventId))
		val eventRepository = object : EventRepository {
			override fun save(event: Event) = error("Publishing is not part of Registration")
			override fun findById(id: EventId): Event? = events.find { it.id == id }
		}
		val stored = mutableListOf<Registration>()
		val registrationRepository = object : RegistrationRepository {
			override fun save(registration: Registration): RegistrationSaveResult {
				stored += registration
				return RegistrationSaveResult.Saved
			}
		}
		val queries = object : RegistrationQueries {
			override fun findForEvent(eventId: EventId): List<Registration> = stored.filter { it.eventId == eventId }
		}
		val application = RegistrationApplication(registrationRepository, queries, eventRepository)

		val registered = assertIs<RegistrationResult.Registered>(
			application.register(eventId, "Ada Lovelace", "ada@example.test"),
		).registration

		assertEquals(eventId, registered.eventId)
		assertEquals(listOf(registered), assertIs<RegistrationReview.Found>(application.review(eventId)).registrations)
		assertEquals(emptyList(), assertIs<RegistrationReview.Found>(application.review(otherEventId)).registrations)
	}

	private fun event(id: EventId): Event = assertIs<EventCreationResult.Created>(Event.publish(
		id = id,
		title = "Kotlin Meetup",
		description = null,
		startAt = Instant.parse("2027-05-10T16:00:00Z"),
		endAt = Instant.parse("2027-05-10T18:00:00Z"),
		timeZone = ZoneId.of("Europe/Warsaw"),
		attendance = EventAttendance.InPerson("Test venue"),
	)).event
}
