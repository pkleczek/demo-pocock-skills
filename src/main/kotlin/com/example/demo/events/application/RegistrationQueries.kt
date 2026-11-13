package com.example.demo.events.application

import com.example.demo.events.domain.Registration
import java.util.UUID

interface RegistrationQueries {
	fun findForEvent(eventId: UUID): List<Registration>
}
