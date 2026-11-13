package com.example.demo.events.domain

interface RegistrationRepository {
	fun save(registration: Registration): RegistrationSaveResult
}

sealed interface RegistrationSaveResult {
	data object Saved : RegistrationSaveResult
	data object AlreadyRegistered : RegistrationSaveResult
}
