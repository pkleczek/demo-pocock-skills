package com.example.demo.events.configuration

import com.example.demo.events.application.EventApplication
import com.example.demo.events.application.EventQueries
import com.example.demo.events.domain.EventRepository
import com.example.demo.events.application.RegistrationApplication
import com.example.demo.events.application.RegistrationQueries
import com.example.demo.events.domain.RegistrationRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class EventConfiguration {
	@Bean
	fun eventApplication(events: EventRepository, queries: EventQueries) = EventApplication(events, queries)

	@Bean
	fun registrationApplication(registrations: RegistrationRepository, queries: RegistrationQueries, events: EventRepository) =
		RegistrationApplication(registrations, queries, events)
}
