package com.example.demo.events.configuration

import com.example.demo.events.application.CreateEvent
import com.example.demo.events.application.EventQueries
import com.example.demo.events.domain.EventRepository
import com.example.demo.events.application.GetEvent
import com.example.demo.events.application.ListEvents
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class EventConfiguration {
	@Bean
	fun createEvent(events: EventRepository) = CreateEvent(events)

	@Bean
	fun getEvent(events: EventQueries) = GetEvent(events)

	@Bean
	fun listEvents(events: EventQueries) = ListEvents(events)
}
