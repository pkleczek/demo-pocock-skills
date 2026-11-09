package com.example.demo.events.inbound

import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice(basePackageClasses = [OrganizerEventController::class])
class EventRequestExceptionHandler : ResponseEntityExceptionHandler() {
	override fun handleMethodArgumentNotValid(
		ex: MethodArgumentNotValidException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any> = validationResponse(
		ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "message" to (it.defaultMessage ?: "is invalid")) },
		headers,
		status,
	)

	override fun handleHandlerMethodValidationException(
		ex: HandlerMethodValidationException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		if (ex.isForReturnValue) return super.handleHandlerMethodValidationException(ex, headers, status, request)
		val errors = ex.parameterValidationResults.flatMap { result ->
			val field = result.methodParameter.getParameterAnnotation(RequestParam::class.java)?.name
				?.takeIf(String::isNotBlank) ?: result.methodParameter.parameterName ?: "request"
			result.resolvableErrors.map { mapOf("field" to field, "message" to (it.defaultMessage ?: "is invalid")) }
		}
		return validationResponse(errors, headers, status)
	}

	override fun handleTypeMismatch(
		ex: TypeMismatchException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		if (ex !is MethodArgumentTypeMismatchException) return super.handleTypeMismatch(ex, headers, status, request)
		val message = if (ex.name == "cursor") "must be a valid cursor" else "has an invalid value"
		return validationResponse(listOf(mapOf("field" to ex.name, "message" to message)), headers, status)
	}

	private fun validationResponse(
		errors: List<Map<String, String>>,
		headers: HttpHeaders,
		status: HttpStatusCode,
	): ResponseEntity<Any> {
		val problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed")
		problem.title = "Invalid request"
		problem.setProperty("errors", errors.sortedBy { it["field"] })
		return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)
	}
}
