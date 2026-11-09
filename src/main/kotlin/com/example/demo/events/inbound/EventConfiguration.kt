package com.example.demo.events.inbound

import com.example.demo.events.application.CreateEvent
import com.example.demo.events.application.EventRepository
import com.example.demo.events.application.GetEvent
import com.example.demo.events.application.ListEvents
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class EventConfiguration {
	@Bean
	fun createEvent(events: EventRepository) = CreateEvent(events)

	@Bean
	fun getEvent(events: EventRepository) = GetEvent(events)

	@Bean
	fun listEvents(events: EventRepository) = ListEvents(events)
}
