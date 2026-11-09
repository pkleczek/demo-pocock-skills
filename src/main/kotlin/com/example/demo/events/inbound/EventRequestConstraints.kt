package com.example.demo.events.inbound

import com.example.demo.events.domain.AttendanceMode
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [EventTimestampValidator::class])
annotation class EventTimestamp(
	val message: String = "must be a UTC timestamp with at most microsecond precision",
	val groups: Array<KClass<*>> = [],
	val payload: Array<KClass<out Payload>> = [],
)

class EventTimestampValidator : ConstraintValidator<EventTimestamp, OffsetDateTime> {
	override fun isValid(value: OffsetDateTime?, context: ConstraintValidatorContext): Boolean {
		if (value == null) return true
		val message = when {
			value.offset != ZoneOffset.UTC -> "must use UTC (Z or +00:00)"
			// PostgreSQL stores microseconds; never silently round a request's timestamps.
			value.nano % 1_000 != 0 -> "must use at most microsecond precision"
			else -> return true
		}
		context.disableDefaultConstraintViolation()
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation()
		return false
	}
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NamedTimeZoneValidator::class])
annotation class NamedTimeZone(
	val message: String = "must be a named IANA time-zone ID",
	val groups: Array<KClass<*>> = [],
	val payload: Array<KClass<out Payload>> = [],
)

class NamedTimeZoneValidator : ConstraintValidator<NamedTimeZone, String> {
	override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
		value == null || value in ZoneId.getAvailableZoneIds()
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [EventAttendanceValidator::class])
annotation class ValidEventAttendance(
	val message: String = "attendance details must match the attendance mode",
	val groups: Array<KClass<*>> = [],
	val payload: Array<KClass<out Payload>> = [],
)

class EventAttendanceValidator : ConstraintValidator<ValidEventAttendance, CreateEventRequest> {
	override fun isValid(value: CreateEventRequest?, context: ConstraintValidatorContext): Boolean {
		if (value == null) return true
		var valid = true
		context.disableDefaultConstraintViolation()
		fun reject(field: String, message: String) {
			context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation()
			valid = false
		}
		when (value.attendanceMode) {
			AttendanceMode.IN_PERSON -> {
				if (value.location == null) reject("location", "is required for an in-person Event")
				if (value.joinUrl != null) reject("joinUrl", "must be omitted for an in-person Event")
			}
			AttendanceMode.ONLINE -> {
				if (value.joinUrl == null) reject("joinUrl", "is required for an online Event")
				if (value.location != null) reject("location", "must be omitted for an online Event")
			}
		}
		return valid
	}
}
