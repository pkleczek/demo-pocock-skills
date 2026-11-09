package com.example.demo.events.domain

sealed interface EventTitleCreationResult {
	data class Created(val title: EventTitle) : EventTitleCreationResult
	data class Rejected(val violations: List<EventViolation>) : EventTitleCreationResult
}

@JvmInline
value class EventTitle private constructor(val value: String) {
	companion object {
		fun create(value: String): EventTitleCreationResult {
			val violations = buildList {
				if (value.isBlank()) add(EventViolation("title", "must not be blank"))
				if (value.length > MAX_LENGTH) {
					add(EventViolation("title", "must be at most $MAX_LENGTH characters"))
				}
			}
			return if (violations.isEmpty()) {
				EventTitleCreationResult.Created(EventTitle(value))
			} else {
				EventTitleCreationResult.Rejected(violations)
			}
		}

		private const val MAX_LENGTH = 200
	}
}
