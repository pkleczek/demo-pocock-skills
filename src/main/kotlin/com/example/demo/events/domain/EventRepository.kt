package com.example.demo.events.domain

import java.util.UUID

interface EventRepository {
	fun save(event: Event)
	fun findById(id: UUID): Event?
}
