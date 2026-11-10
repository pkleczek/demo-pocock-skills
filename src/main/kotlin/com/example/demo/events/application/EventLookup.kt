package com.example.demo.events.application

import com.example.demo.events.domain.Event

sealed interface EventLookup {
	data class Found(val event: Event) : EventLookup
	data object NotFound : EventLookup
}
