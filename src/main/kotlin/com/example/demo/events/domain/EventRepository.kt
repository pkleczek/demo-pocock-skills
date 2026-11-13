package com.example.demo.events.domain

interface EventRepository {
	fun save(event: Event)
	fun findById(id: EventId): Event?
}
