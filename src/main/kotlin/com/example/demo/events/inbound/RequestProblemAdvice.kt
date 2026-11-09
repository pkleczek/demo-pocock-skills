package com.example.demo.events.inbound

import com.example.demo.events.application.EventCursor
import org.springframework.beans.TypeMismatchException
import org.springframework.core.MethodParameter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingMatrixVariableException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.exc.InvalidNullException
import tools.jackson.databind.exc.MismatchedInputException
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestProblemAdvice(private val objectMapper: ObjectMapper) : ResponseEntityExceptionHandler() {
	override fun handleMissingPathVariable(
		ex: MissingPathVariableException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		if (status.value() != 400) return super.handleMissingPathVariable(ex, headers, status, request)
		val errors = listOf(mapOf(
			"field" to ex.variableName, "message" to expectedTypeMessage(ex.parameter.parameterType),
		))
		return handleWithErrors(ex, ex.body, headers, status, request, errors)
	}

	override fun handleServletRequestBindingException(
		ex: ServletRequestBindingException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		if (status.value() != 400) return super.handleServletRequestBindingException(ex, headers, status, request)
		val problem = createProblemDetail(ex, status, "Request parameters are invalid", null, null, request)
		val error = when (ex) {
			is MissingRequestHeaderException -> mapOf("field" to ex.headerName, "message" to "is required")
			is MissingMatrixVariableException -> mapOf("field" to ex.variableName, "message" to "is required")
			else -> mapOf("field" to "request", "message" to "contains invalid request parameters")
		}
		return handleWithErrors(ex, problem, headers, status, request, listOf(error))
	}

	override fun handleMissingServletRequestParameter(
		ex: MissingServletRequestParameterException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val errors = listOf(mapOf("field" to ex.parameterName, "message" to "is required"))
		return handleWithErrors(ex, ex.body, headers, status, request, errors)
	}

	override fun handleTypeMismatch(
		ex: TypeMismatchException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val problem = createProblemDetail(ex, status, "Request parameter is invalid", null, null, request)
		val field = (ex as? MethodArgumentTypeMismatchException)?.name ?: ex.propertyName ?: "request"
		val message = expectedTypeMessage(ex.requiredType)
		return handleWithErrors(ex, problem, headers, status, request, listOf(mapOf("field" to field, "message" to message)))
	}

	override fun handleHandlerMethodValidationException(
		ex: HandlerMethodValidationException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		if (status.value() != 400) return super.handleHandlerMethodValidationException(ex, headers, status, request)
		val errors = ex.parameterValidationResults.flatMap { result ->
			val parameter = result.methodParameter
			val query = parameter.getParameterAnnotation(RequestParam::class.java)
			val path = parameter.getParameterAnnotation(PathVariable::class.java)
			val field = query?.name?.ifBlank { query.value }?.ifBlank { null }
				?: path?.name?.ifBlank { path.value }?.ifBlank { null }
				?: parameter.parameterName ?: "request"
			result.resolvableErrors.map {
				mapOf("field" to if (it is FieldError) externalField(parameter, it.field) else field, "message" to (it.defaultMessage ?: "is invalid"))
			}
		} + ex.crossParameterValidationResults.map {
			mapOf("field" to "request", "message" to (it.defaultMessage ?: "is invalid"))
		}
		return handleWithErrors(ex, ex.body, headers, status, request, errors)
	}

	override fun handleMethodArgumentNotValid(
		ex: MethodArgumentNotValidException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val errors = ex.bindingResult.allErrors.map {
			val message = if (it is FieldError && it.isBindingFailure) {
				expectedTypeMessage(ex.bindingResult.getFieldType(it.field))
			} else {
				it.defaultMessage ?: "is invalid"
			}
			mapOf("field" to if (it is FieldError) externalField(ex.parameter, it.field) else "body", "message" to message)
		}
		return handleWithErrors(ex, ex.body, headers, status, request, errors)
	}

	override fun handleHttpMessageNotReadable(
		ex: HttpMessageNotReadableException,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val problem = createProblemDetail(ex, status, "Request body is invalid", null, null, request)
		val failure = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<MismatchedInputException>().firstOrNull()
		val error = if (failure is InvalidNullException) {
			mapOf("field" to failure.propertyName.simpleName, "message" to "is required")
		} else if (failure != null && failure.path.isNotEmpty()) {
			val field = failure.path.mapNotNull { it.propertyName }.joinToString(".")
			val type = failure.targetType
			val message = when {
				type == String::class.java -> "must be a string"
				type == OffsetDateTime::class.java -> "must be an ISO-8601 timestamp with an offset"
				type == URI::class.java -> "must be a valid URI"
				type?.isEnum == true -> "must be one of: " + type.enumConstants.joinToString(", ") { (it as Enum<*>).name }
				else -> "has an invalid value"
			}
			mapOf("field" to field, "message" to message)
		} else if (failure != null || ex.cause == null) {
			mapOf("field" to "body", "message" to "must contain a JSON object")
		} else {
			mapOf("field" to "body", "message" to "must be valid JSON")
		}
		return handleWithErrors(ex, problem, headers, status, request, listOf(error))
	}

	private fun handleWithErrors(
		ex: Exception,
		problem: ProblemDetail,
		headers: HttpHeaders,
		status: HttpStatusCode,
		request: WebRequest,
		errors: List<Map<String, String>>,
	): ResponseEntity<Any>? {
		problem.setProperty("errors", errors)
		return handleExceptionInternal(ex, problem, headers, status, request)
	}

	private fun externalField(parameter: MethodParameter, path: String): String {
		if (!parameter.hasParameterAnnotation(RequestBody::class.java)) return path
		val config = objectMapper.deserializationConfig()
		val introspector = config.classIntrospectorInstance().forOperation(config)
		var type = objectMapper.constructType(parameter.genericParameterType)
		val external = mutableListOf<String>()
		for (segment in path.split('.')) {
			val name = segment.substringBefore('[')
			val indexes = segment.removePrefix(name)
			val properties = introspector.introspectForDeserialization(type, introspector.introspectClassAnnotations(type)).findProperties()
			val property = properties.firstOrNull { it.internalName == name } ?: return path
			type = property.primaryType
			repeat(indexes.count { it == '[' }) { type = type.contentType ?: return path }
			external += property.name + indexes
		}
		return external.joinToString(".")
	}

	private fun expectedTypeMessage(type: Class<*>?): String = when (type) {
		Int::class.java, Int::class.javaObjectType -> "must be an integer"
		UUID::class.java -> "must be a valid UUID"
		EventCursor::class.java -> "must be a valid cursor returned by the Event list; omit it to start from the first page"
		else -> "has an invalid value"
	}
}
