package com.example.demo.events

import com.jayway.jsonpath.JsonPath
import com.example.demo.TestcontainersConfiguration
import com.example.demo.events.inbound.CreateEventRequest
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraintvalidation.SupportedValidationTarget
import jakarta.validation.constraintvalidation.ValidationTarget
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.MatrixVariable
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class, EventHttpApiTests.RequestErrorProbeController::class)
@AutoConfigureMockMvc
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["organizer.api-key=test-organizer-key"],
)
class EventHttpApiTests @Autowired constructor(
	private val mockMvc: MockMvc,
	private val jdbcTemplate: JdbcTemplate,
	@LocalServerPort private val port: Int,
) {
	@BeforeEach
	fun clearEvents() {
		jdbcTemplate.update("DELETE FROM events")
	}

	@ParameterizedTest
	@ValueSource(strings = [
		"/api/organizer/events",
		"/api/%6frganizer/events",
	])
	fun `Organizer authentication applies to every routed spelling of the path`(path: String) {
		HttpClient.newHttpClient().use { client ->
			for (credential in listOf(null, "Bearer wrong-organizer-key", "Bearer test-organizer-key")) {
				val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.POST(HttpRequest.BodyPublishers.ofString(inPersonEventJson()))
				if (credential != null) request.header(HttpHeaders.AUTHORIZATION, credential)

				val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
				val expectedStatus = if (credential == "Bearer test-organizer-key") 201 else 401
				assertEquals(expectedStatus, response.statusCode(), "Unexpected authentication result for $path")
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = [
		"/api/organizer;x=1/events",
		"/api;x=1/organizer/events",
		"/api/%6frganizer;x=1/events",
	])
	fun `Spring Security firewall rejects matrix-parameter paths even with valid credentials`(path: String) {
		HttpClient.newHttpClient().use { client ->
			for (credential in listOf(null, "Bearer test-organizer-key")) {
				val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.POST(HttpRequest.BodyPublishers.ofString(inPersonEventJson()))
				if (credential != null) request.header(HttpHeaders.AUTHORIZATION, credential)

				val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
				assertEquals(400, response.statusCode(), "Unsafe path must be rejected: $path")
			}
		}
		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))
	}

	@Test
	fun `Organizer can publish an in-person Event`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Kotlin in the Park",
					  "description": "An afternoon of talks",
					  "startAt": "2027-05-10T16:00:00Z",
					  "endAt": "2027-05-10T18:30:00Z",
					  "timeZone": "America/Los_Angeles",
					  "attendanceMode": "IN_PERSON",
					  "location": "Pioneer Courthouse Square, Portland"
					}
					""".trimIndent(),
				),
		)
			.andExpect(status().isCreated)
			.andExpect(header().exists(HttpHeaders.LOCATION))
			.andExpect(jsonPath("$.id").isNotEmpty)
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.attendanceMode").value("IN_PERSON"))
			.andExpect(jsonPath("$.timeZone").value("America/Los_Angeles"))
			.andExpect(jsonPath("$.location").value("Pioneer Courthouse Square, Portland"))
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
	}

	@Test
	fun `Organizer can publish an online Event without exposing its join URL`() {
		val createResponse = mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Remote Kotlin Meetup",
					  "startAt": "2027-07-12T15:00:00Z",
					  "endAt": "2027-07-12T17:00:00Z",
					  "timeZone": "Europe/Stockholm",
					  "attendanceMode": "ONLINE",
					  "joinUrl": "https://meet.example.test/secret-room"
					}
					""".trimIndent(),
				),
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.attendanceMode").value("ONLINE"))
			.andExpect(jsonPath("$.location").doesNotExist())
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
			.andReturn()

		val eventId = createResponse.response.getHeader(HttpHeaders.LOCATION)!!
			.substringAfterLast('/')
		mockMvc.perform(get("/api/events/$eventId"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.attendanceMode").value("ONLINE"))
			.andExpect(jsonPath("$.location").doesNotExist())
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
	}

	@Test
	fun `Organizer cannot create an Event with a non-UTC timestamp`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Offset Event",
					  "startAt": "2027-08-12T16:00:00+01:00",
					  "endAt": "2027-08-12T18:00:00Z",
					  "timeZone": "Europe/Stockholm",
					  "attendanceMode": "ONLINE",
					  "joinUrl": "https://meet.example.test/offset-room"
					}
					""".trimIndent(),
				),
		)
			.andExpect(status().isBadRequest)
			.andExpect(invalidBodyProblem(mapOf("field" to "startAt", "message" to "must use UTC (Z or +00:00)")))
	}

	@ParameterizedTest
	@CsvSource(
		"2027-05-10T16:00:00.123456789Z, 2027-05-10T18:00:00Z, startAt",
		"2027-05-10T16:00:00Z, 2027-05-10T18:00:00.123456789Z, endAt",
		"2027-05-10T16:00:00.000000100Z, 2027-05-10T16:00:00.000000200Z, startAt endAt",
	)
	fun `timestamps finer than microseconds receive a clear validation error`(
		startAt: String,
		endAt: String,
		fields: String,
	) {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(startAt = startAt, endAt = endAt)),
		)
			.andExpect(invalidBodyProblem(
				*fields.split(' ').map { mapOf("field" to it, "message" to "must use at most microsecond precision") }.toTypedArray(),
			))
	}

	@ParameterizedTest
	@ValueSource(strings = ["{not-json-secret", "{\"title\":", "\"unterminated-secret"])
	fun `malformed JSON receives a safe body-level error`(body: String) {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to "body", "message" to "must be valid JSON")))
			.andExpect(content().string(not(containsString("secret"))))
	}

	@ParameterizedTest
	@ValueSource(strings = ["", "null", "[]", "42", "\"secret\""])
	fun `empty or non-object bodies explain the required request shape`(body: String) {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to "body", "message" to "must contain a JSON object")))
	}

	@Test
	fun `domain rejections are mapped to structured client errors`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(endAt = "2027-05-10T15:00:00Z")),
		)
			.andExpect(status().isBadRequest)
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.title").value("Invalid Event details"))
			.andExpect(jsonPath("$.errors[0].field").value("endAt"))
			.andExpect(jsonPath("$.errors[0].message").value("must be after startAt"))
	}

	@Test
	fun `Bean Validation rejects blank titles before invoking the use case`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(title = " ")),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to "title", "message" to "must not be blank")))
	}

	@Test
	fun `Bean Validation enforces the request title size limit`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(title = "a".repeat(201))),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to "title", "message" to "size must be between 0 and 200")))
	}

	@Test
	fun `all field and class-level attendance validation failures are returned together`() {
		val body = JsonPath.parse(inPersonEventJson(title = " "))
			.set("$.startAt", "2027-05-10T16:00:00+01:00")
			.set("$.endAt", "2027-05-10T18:00:00.123456789Z")
			.set("$.timeZone", "private-invalid-zone")
			.set("$.attendanceMode", "ONLINE")
			.jsonString()
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(invalidBodyProblem(
				mapOf("field" to "title", "message" to "must not be blank"),
				mapOf("field" to "startAt", "message" to "must use UTC (Z or +00:00)"),
				mapOf("field" to "endAt", "message" to "must use at most microsecond precision"),
				mapOf("field" to "timeZone", "message" to "must be a named IANA time-zone ID"),
				mapOf("field" to "joinUrl", "message" to "is required for an online Event"),
				mapOf("field" to "location", "message" to "must be omitted for an online Event"),
			))
			.andExpect(content().string(not(containsString("private-invalid-zone"))))
	}

	@ParameterizedTest
	@CsvSource(
		"title, false", "startAt, false", "endAt, false", "timeZone, false", "attendanceMode, false",
		"title, true", "startAt, true", "endAt, true", "timeZone, true", "attendanceMode, true",
	)
	fun `missing or null required request properties name the field`(field: String, explicitNull: Boolean) {
		val json = JsonPath.parse(inPersonEventJson())
		val body = if (explicitNull) json.set("$.$field", null).jsonString() else json.delete("$.$field").jsonString()
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to field, "message" to "is required")))
	}

	@ParameterizedTest
	@CsvSource(
		"startAt, not-a-timestamp, must be an ISO-8601 timestamp with an offset",
		"startAt, 2027-02-30T16:00:00Z, must be an ISO-8601 timestamp with an offset",
		"startAt, 2027-05-10T16:00:00, must be an ISO-8601 timestamp with an offset",
		"endAt, not-a-timestamp, must be an ISO-8601 timestamp with an offset",
		"attendanceMode, HYBRID, 'must be one of: IN_PERSON, ONLINE'",
		"joinUrl, https://meet.example.test/a b, must be a valid URI",
	)
	fun `malformed typed request properties describe the expected input safely`(field: String, value: String, message: String) {
		val body = JsonPath.parse(inPersonEventJson()).put("$", field, value).jsonString()
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to field, "message" to message)))
			.andExpect(content().string(not(containsString(value))))
	}

	@ParameterizedTest
	@ValueSource(strings = ["title", "timeZone"])
	fun `non-string request properties explain the expected JSON type`(field: String) {
		val body = JsonPath.parse(inPersonEventJson()).set("$.$field", listOf("sensitive-value")).jsonString()
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to field, "message" to "must be a string")))
			.andExpect(content().string(not(containsString("sensitive-value"))))
	}

	@ParameterizedTest
	@CsvSource("IN_PERSON, location", "ONLINE, joinUrl")
	fun `Bean Validation requires the attendance-mode-specific property`(mode: String, field: String) {
		val body = JsonPath.parse(inPersonEventJson())
			.set("$.attendanceMode", mode)
			.delete("$.location")
			.jsonString()
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(invalidBodyProblem(mapOf(
				"field" to field,
				"message" to if (mode == "IN_PERSON") "is required for an in-person Event" else "is required for an online Event",
			)))
	}

	@Test
	fun `Organizer receives a clear validation error for an unknown time zone`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Unknown Zone Event",
					  "startAt": "2027-10-12T16:00:00Z",
					  "endAt": "2027-10-12T18:00:00Z",
					  "timeZone": "Mars/Olympus",
					  "attendanceMode": "IN_PERSON",
					  "location": "Test location"
					}
					""".trimIndent(),
				),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to "timeZone", "message" to "must be a named IANA time-zone ID")))
	}

	@Test
	fun `Organizer must provide a named time zone instead of a fixed offset`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Fixed Offset Zone Event",
					  "startAt": "2027-10-12T16:00:00Z",
					  "endAt": "2027-10-12T18:00:00Z",
					  "timeZone": "+02:00",
					  "attendanceMode": "IN_PERSON",
					  "location": "Test location"
					}
					""".trimIndent(),
				),
		)
			.andExpect(invalidBodyProblem(mapOf("field" to "timeZone", "message" to "must be a named IANA time-zone ID")))
	}

	@ParameterizedTest
	@ValueSource(strings = ["IN_PERSON", "ONLINE"])
	fun `Bean Validation rejects attendance details for the wrong mode`(mode: String) {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Hybrid Event",
					  "startAt": "2027-10-12T16:00:00Z",
					  "endAt": "2027-10-12T18:00:00Z",
					  "timeZone": "America/Los_Angeles",
					  "attendanceMode": "$mode",
					  "location": "Test venue",
					  "joinUrl": "https://meet.example.test/hybrid-room"
					}
					""".trimIndent(),
				),
		)
			.andExpect(invalidBodyProblem(mapOf(
				"field" to if (mode == "IN_PERSON") "joinUrl" else "location",
				"message" to if (mode == "IN_PERSON") "must be omitted for an in-person Event" else "must be omitted for an online Event",
			)))
	}

	@Test
	fun `unknown Events retain the empty not-found response`() {
		mockMvc.perform(get("/api/events/5319b0e7-387c-40c6-8edc-b4b11fb89c86"))
			.andExpect(status().isNotFound)
			.andExpect(content().string(""))
	}

	@Test
	fun `public clients can retrieve a published Event by ID`() {
		val createResponse = mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Accessible Public Event",
					  "startAt": "2027-06-12T15:00:00.123456000Z",
					  "endAt": "2027-06-12T17:00:00.654321Z",
					  "timeZone": "America/Los_Angeles",
					  "attendanceMode": "IN_PERSON",
					  "location": "Rose Garden, Portland"
					}
					""".trimIndent(),
			)
		).andExpect(status().isCreated)
			.andExpect(jsonPath("$.startAt").value("2027-06-12T15:00:00.123456Z"))
			.andExpect(jsonPath("$.endAt").value("2027-06-12T17:00:00.654321Z"))
			.andReturn()

		val eventId = createResponse.response.getHeader(HttpHeaders.LOCATION)!!
			.substringAfterLast('/')

		mockMvc.perform(get("/api/events/$eventId"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.id").value(eventId))
			.andExpect(jsonPath("$.title").value("Accessible Public Event"))
			.andExpect(jsonPath("$.startAt").value("2027-06-12T15:00:00.123456Z"))
			.andExpect(jsonPath("$.endAt").value("2027-06-12T17:00:00.654321Z"))
			.andExpect(jsonPath("$.timeZone").value("America/Los_Angeles"))
			.andExpect(jsonPath("$.attendanceMode").value("IN_PERSON"))
			.andExpect(jsonPath("$.location").value("Rose Garden, Portland"))
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
	}

	@Test
	fun `public event list supports cursor pages in chronological order`() {
		publishInPersonEvent(
			"Middle Event",
			"2027-05-10T16:00:00Z",
			"2027-05-10T18:00:00Z",
		)
		publishInPersonEvent(
			"Past Event",
			"2026-05-10T16:00:00Z",
			"2026-05-10T18:00:00Z",
		)
		publishInPersonEvent(
			"Future Event",
			"2027-06-10T16:00:00Z",
			"2027-06-10T18:00:00Z",
		)

		val firstPage = mockMvc.perform(get("/api/events").param("limit", "2"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].title").value("Past Event"))
			.andExpect(jsonPath("$.items[1].title").value("Middle Event"))
			.andExpect(jsonPath("$.nextCursor").isNotEmpty)
			.andReturn()

		val cursor = JsonPath.read<String>(firstPage.response.contentAsString, "$.nextCursor")

		mockMvc.perform(get("/api/events").param("limit", "2").param("cursor", cursor))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Future Event"))
			.andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()))
	}

	@Test
	fun `public event list excludes cancelled Events but their details remain retrievable`() {
		val eventId = publishInPersonEvent(
			"Cancelled Event",
			"2027-05-10T16:00:00Z",
			"2027-05-10T18:00:00Z",
		)
		jdbcTemplate.update(
			"UPDATE events SET status = ? WHERE id = ?",
			"CANCELLED",
			UUID.fromString(eventId),
		)

		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))

		mockMvc.perform(get("/api/events/$eventId"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.status").value("CANCELLED"))
	}

	@ParameterizedTest
	@ValueSource(ints = [-1, 0, 101])
	fun `Bean Validation enforces the public event list page size`(limit: Int) {
		mockMvc.perform(get("/api/events").param("limit", limit.toString()))
			.andExpect(invalidRequestProblem(listOf(mapOf(
				"field" to "limit",
				"message" to if (limit < 1) "must be greater than or equal to 1" else "must be less than or equal to 100",
			))))
	}

	@ParameterizedTest
	@ValueSource(strings = ["abc", "2147483648"])
	fun `Spring rejects page sizes that cannot bind to an integer`(limit: String) {
		mockMvc.perform(get("/api/events").param("limit", limit))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "limit", "message" to "must be an integer"))))
			.andExpect(content().string(not(containsString(limit))))
	}

	@Test
	fun `public event list rejects an invalid cursor`() {
		mockMvc.perform(get("/api/events").param("cursor", "%%%"))
			.andExpect(invalidRequestProblem(listOf(mapOf(
				"field" to "cursor", "message" to "must be a valid cursor returned by the Event list; omit it to start from the first page",
			))))
			.andExpect(content().string(not(containsString("%%%"))))
	}

	@ParameterizedTest
	@ValueSource(strings = [
		"missing-separator",
		"not-a-timestamp|5319b0e7-387c-40c6-8edc-b4b11fb89c86",
		"2027-05-10T16:00:00Z|not-a-uuid",
	])
	fun `Spring cursor binding rejects malformed cursor contents`(contents: String) {
		val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(contents.toByteArray(Charsets.UTF_8))
		mockMvc.perform(get("/api/events").param("cursor", cursor))
			.andExpect(invalidRequestProblem(listOf(mapOf(
				"field" to "cursor", "message" to "must be a valid cursor returned by the Event list; omit it to start from the first page",
			))))
			.andExpect(content().string(not(containsString(cursor))))
			.andExpect(content().string(not(containsString(contents))))
	}

	@ParameterizedTest
	@ValueSource(strings = ["not-a-uuid", " "])
	fun `invalid Event identifiers explain the expected UUID path value`(id: String) {
		mockMvc.perform(get("/api/events/{id}", id))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "id", "message" to "must be a valid UUID"))))
			.andExpect(jsonPath("$.detail", not(containsString("not-a-uuid"))))
			.andExpect(jsonPath("$.errors[0].message", not(containsString("not-a-uuid"))))
	}

	@Test
	fun `missing required query parameters use their external request names`() {
		mockMvc.perform(get("/api/events/request-errors/required-query"))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "page-size", "message" to "is required"))))
	}

	@Test
	fun `MVC query-object binding returns safe field guidance instead of conversion diagnostics`() {
		mockMvc.perform(get("/api/events/request-errors/query-object").param("limit", "private-invalid-number"))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "limit", "message" to "must be an integer"))))
			.andExpect(content().string(not(containsString("private-invalid-number"))))
	}

	@Test
	fun `method validation returns all query and path errors using external aliases`() {
		mockMvc.perform(get("/api/events/request-errors/validation/0").param("page-size", "0"))
			.andExpect(invalidRequestProblem(listOf(
				mapOf("field" to "page-size", "message" to "must be greater than or equal to 1"),
				mapOf("field" to "sequence", "message" to "must be greater than or equal to 1"),
			)))
	}

	@Test
	fun `method validation preserves body field errors alongside query errors`() {
		mockMvc.perform(
			post("/api/events/request-errors/body-validation")
				.param("page-size", "0")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(title = " ")),
		)
			.andExpect(invalidRequestProblem(listOf(
				mapOf("field" to "page-size", "message" to "must be greater than or equal to 1"),
				mapOf("field" to "title", "message" to "must not be blank"),
			)))
	}

	@Test
	fun `body validation uses external JSON aliases and configured nested property names`() {
		mockMvc.perform(
			post("/api/events/request-errors/aliased-body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"event-title":" ","event-details":[{"display_title":" "}]}"""),
		)
			.andExpect(invalidRequestProblem(listOf(
				mapOf("field" to "event-title", "message" to "must not be blank"),
				mapOf("field" to "event-details[0].display_title", "message" to "must not be blank"),
			)))
	}

	@Test
	fun `method validation combines external JSON field paths with query parameter names`() {
		mockMvc.perform(
			post("/api/events/request-errors/aliased-body-validation")
				.param("page-size", "0")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"event-title":" ","event-details":[{"display_title":" "}]}"""),
		)
			.andExpect(invalidRequestProblem(listOf(
				mapOf("field" to "page-size", "message" to "must be greater than or equal to 1"),
				mapOf("field" to "event-title", "message" to "must not be blank"),
				mapOf("field" to "event-details[0].display_title", "message" to "must not be blank"),
			)))
	}

	@Test
	fun `method validation includes cross-parameter constraints alongside parameter errors`() {
		mockMvc.perform(
			get("/api/events/request-errors/cross-validation")
				.param("from", "2").param("to", "1").param("page-size", "0"),
		)
			.andExpect(invalidRequestProblem(listOf(
				mapOf("field" to "page-size", "message" to "must be greater than or equal to 1"),
				mapOf("field" to "request", "message" to "from must not exceed to"),
			)))
	}

	@ParameterizedTest
	@ValueSource(strings = ["named-query-object", "named-query-validation"])
	fun `query-object validation does not apply JSON property naming`(path: String) {
		mockMvc.perform(
			get("/api/events/request-errors/$path").param("displayTitle", " ").param("page-size", "1"),
		)
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "displayTitle", "message" to "must not be blank"))))
	}

	@Test
	fun `missing required headers return structured binding errors with external names`() {
		mockMvc.perform(get("/api/events/request-errors/required-header"))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "X-Page-Size", "message" to "is required"))))
	}

	@Test
	fun `unrecognized parameter types receive a safe conversion fallback`() {
		mockMvc.perform(get("/api/events/request-errors/unknown-type").param("enabled", "private-invalid-boolean"))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "enabled", "message" to "has an invalid value"))))
			.andExpect(content().string(not(containsString("private-invalid-boolean"))))
	}

	@Test
	fun `missing matrix variables use their external request names`() {
		mockMvc.perform(get("/api/events/request-errors/unknown-binding"))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "revision", "message" to "is required"))))
			.andExpect(content().string(not(containsString("internalRevision"))))
	}

	@Test
	fun `MVC binding failures without structured field information retain a safe request fallback`() {
		mockMvc.perform(get("/api/events/request-errors/request-attribute"))
			.andExpect(invalidRequestProblem(listOf(mapOf("field" to "request", "message" to "contains invalid request parameters"))))
			.andExpect(content().string(not(containsString("internalContext"))))
	}

	@ParameterizedTest
	@ValueSource(strings = ["invalid-return", "missing-path-mapping"])
	fun `server-side validation and path mapping defects remain server errors`(path: String) {
		mockMvc.perform(get("/api/events/request-errors/$path"))
			.andExpect(status().isInternalServerError)
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(500))
			.andExpect(jsonPath("$.errors").doesNotExist())
	}

	@Test
	fun `unsupported HTTP methods retain their status and allowed methods`() {
		mockMvc.perform(get("/api/organizer/events").header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"))
			.andExpect(status().isMethodNotAllowed)
			.andExpect(header().string(HttpHeaders.ALLOW, "POST"))
			.andExpect(jsonPath("$.status").value(405))
			.andExpect(jsonPath("$.errors").doesNotExist())
	}

	@Test
	fun `unsupported request media types retain their non-400 response`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.TEXT_PLAIN)
				.content("{}"),
		)
			.andExpect(status().isUnsupportedMediaType)
			.andExpect(jsonPath("$.status").value(415))
			.andExpect(jsonPath("$.errors").doesNotExist())
	}

	// Test-only routes exercise MVC failures not reachable through today's optional Event-list parameters.
	@RestController
	class RequestErrorProbeController {
		@GetMapping("/api/events/request-errors/required-query")
		fun requiredQuery(@RequestParam("page-size") internalLimit: Int) = internalLimit

		@GetMapping("/api/events/request-errors/query-object")
		fun queryObject(@ModelAttribute query: PageSizeProbe) = query.limit

		@GetMapping("/api/events/request-errors/named-query-object")
		fun namedQueryObject(@Valid @ModelAttribute query: NamedDetailsProbe) = query.displayTitle

		@GetMapping("/api/events/request-errors/named-query-validation")
		fun namedQueryValidation(
			@Min(1) @RequestParam("page-size") internalLimit: Int,
			@Valid @ModelAttribute query: NamedDetailsProbe,
		) = "$internalLimit ${query.displayTitle}"

		@GetMapping("/api/events/request-errors/validation/{sequence}")
		fun validation(
			@Min(1) @RequestParam("page-size") internalLimit: Int,
			@Min(1) @PathVariable("sequence") internalSequence: Int,
		) = internalLimit + internalSequence

		@PostMapping("/api/events/request-errors/body-validation")
		fun bodyValidation(
			@Min(1) @RequestParam("page-size") internalLimit: Int,
			@Valid @RequestBody body: CreateEventRequest,
		) = "$internalLimit ${body.title}"

		@PostMapping("/api/events/request-errors/aliased-body")
		fun aliasedBody(@Valid @RequestBody body: AliasedBodyProbe) = body.internalTitle

		@PostMapping("/api/events/request-errors/aliased-body-validation")
		fun aliasedBodyValidation(
			@Min(1) @RequestParam("page-size") internalLimit: Int,
			@Valid @RequestBody body: AliasedBodyProbe,
		) = "$internalLimit ${body.internalTitle}"

		@OrderedRange
		@GetMapping("/api/events/request-errors/cross-validation")
		fun crossValidation(
			@RequestParam("from") internalFrom: Int,
			@RequestParam("to") internalTo: Int,
			@Min(1) @RequestParam("page-size") internalLimit: Int,
		) = "$internalFrom $internalTo $internalLimit"

		@GetMapping("/api/events/request-errors/required-header")
		fun requiredHeader(@RequestHeader("X-Page-Size") internalLimit: Int) = internalLimit

		@GetMapping("/api/events/request-errors/unknown-type")
		fun unknownType(@RequestParam("enabled") enabled: Boolean) = enabled

		@GetMapping("/api/events/request-errors/unknown-binding")
		fun unknownBinding(@MatrixVariable("revision") internalRevision: Int) = internalRevision

		@GetMapping("/api/events/request-errors/request-attribute")
		fun requestAttribute(@RequestAttribute("context") internalContext: String) = internalContext

		@Min(1)
		@GetMapping("/api/events/request-errors/invalid-return")
		fun invalidReturn() = 0

		@GetMapping("/api/events/request-errors/missing-path-mapping")
		fun missingPathMapping(@PathVariable("missing") id: UUID) = id
	}

	data class PageSizeProbe(@param:JsonProperty("page-size") val limit: Int)

	data class AliasedBodyProbe(
		@param:JsonProperty("event-title") @field:NotBlank val internalTitle: String,
		@param:JsonProperty("event-details") @field:Valid val internalDetails: List<NamedDetailsProbe>,
	)

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
	data class NamedDetailsProbe(@field:NotBlank val displayTitle: String)

	@Target(AnnotationTarget.FUNCTION)
	@Retention(AnnotationRetention.RUNTIME)
	@Constraint(validatedBy = [OrderedRangeValidator::class])
	annotation class OrderedRange(
		val message: String = "from must not exceed to",
		val groups: Array<KClass<*>> = [],
		val payload: Array<KClass<out Payload>> = [],
	)

	@SupportedValidationTarget(ValidationTarget.PARAMETERS)
	class OrderedRangeValidator : ConstraintValidator<OrderedRange, Array<Any?>> {
		override fun isValid(value: Array<Any?>, context: ConstraintValidatorContext): Boolean =
			(value[0] as Int) <= (value[1] as Int)
	}

	private fun invalidBodyProblem(vararg errors: Map<String, String>) = invalidRequestProblem(errors.toList())

	private fun invalidRequestProblem(errors: List<Map<String, String>>) = ResultMatcher { result ->
		status().isBadRequest.match(result)
		content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result)
		jsonPath("$.status").value(400).match(result)
		// RFC 9457 defaults an omitted type to about:blank.
		val metadata = JsonPath.read<Map<String, Any>>(result.response.contentAsString, "$")
		assertEquals("about:blank", metadata["type"] ?: "about:blank")
		jsonPath("$.title").value("Bad Request").match(result)
		jsonPath("$.instance").value(result.request.requestURI).match(result)
		jsonPath("$.detail").isNotEmpty.match(result)
		for (diagnostic in listOf("Exception", "com.example", "tools.jackson", "org.springframework", "java.lang", "Failed to convert", "For input string")) {
			content().string(not(containsString(diagnostic))).match(result)
		}
		jsonPath("$.errors").isNotEmpty.match(result)
		val actual = JsonPath.read<List<Map<String, String>>>(result.response.contentAsString, "$.errors")
		assertEquals(errors.toSet(), actual.toSet())
		assertEquals(errors.size, actual.size)
		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))
	}

	private fun inPersonEventJson(
		title: String = "Test Event",
		startAt: String = "2027-05-10T16:00:00Z",
		endAt: String = "2027-05-10T18:00:00Z",
	): String = """
		{
		  "title": "$title",
		  "startAt": "$startAt",
		  "endAt": "$endAt",
		  "timeZone": "America/Los_Angeles",
		  "attendanceMode": "IN_PERSON",
		  "location": "Test location"
		}
		""".trimIndent()

	private fun publishInPersonEvent(title: String, startAt: String, endAt: String): String {
		val response = mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(title, startAt, endAt)),
		)
			.andExpect(status().isCreated)
			.andReturn()
		return response.response.getHeader(HttpHeaders.LOCATION)!!
			.substringAfterLast('/')
	}
}
