package com.example.demo.events.application

import com.example.demo.events.domain.Registration
import com.example.demo.events.domain.EventId

interface RegistrationQueries {
	fun findForEvent(eventId: EventId): List<Registration>
}
