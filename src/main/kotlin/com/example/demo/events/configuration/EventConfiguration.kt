package com.example.demo.events.configuration

import com.example.demo.events.application.EventApplication
import com.example.demo.events.application.EventQueries
import com.example.demo.events.domain.EventRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class EventConfiguration {
	@Bean
	fun eventApplication(events: EventRepository, queries: EventQueries) = EventApplication(events, queries)
}
