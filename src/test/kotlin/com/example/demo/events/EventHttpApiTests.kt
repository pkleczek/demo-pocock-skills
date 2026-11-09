package com.example.demo.events

import com.jayway.jsonpath.JsonPath
import com.example.demo.TestcontainersConfiguration
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
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

	@Test
	fun `Organizer API rejects requests without the configured credential`() {
		mockMvc.perform(post("/api/organizer/events"))
			.andExpect(status().isUnauthorized)
	}

	@Test
	fun `Organizer API rejects requests with an invalid credential`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer wrong-organizer-key"),
		)
			.andExpect(status().isUnauthorized)
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
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.detail").isNotEmpty)
	}

	@ParameterizedTest
	@CsvSource(
		"2027-05-10T16:00:00.123456789Z, 2027-05-10T18:00:00Z",
		"2027-05-10T16:00:00Z, 2027-05-10T18:00:00.123456789Z",
		"2027-05-10T16:00:00.000000100Z, 2027-05-10T16:00:00.000000200Z",
	)
	fun `timestamps finer than microseconds receive a clear validation error`(
		startAt: String,
		endAt: String,
	) {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(startAt = startAt, endAt = endAt)),
		)
			.andExpect(status().isBadRequest)
			.andExpect(invalidRequestProblem())

		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))
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
			.andExpect(status().isBadRequest)
			.andExpect(invalidRequestProblem())
	}

	@Test
	fun `Bean Validation enforces the request title size limit`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(inPersonEventJson(title = "a".repeat(201))),
		)
			.andExpect(status().isBadRequest)
			.andExpect(invalidRequestProblem())
	}

	@ParameterizedTest
	@ValueSource(strings = ["title", "startAt", "endAt", "timeZone", "attendanceMode"])
	fun `missing required request properties receive a client error`(field: String) {
		val body = JsonPath.parse(inPersonEventJson()).delete("$.$field").jsonString()
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(invalidRequestProblem())
	}

	@ParameterizedTest
	@CsvSource(
		"startAt, not-a-timestamp",
		"startAt, 2027-02-30T16:00:00Z",
		"startAt, 2027-05-10T16:00:00",
		"endAt, not-a-timestamp",
		"attendanceMode, HYBRID",
		"joinUrl, https://meet.example.test/a b",
	)
	fun `Spring rejects malformed typed request properties`(field: String, value: String) {
		val body = JsonPath.parse(inPersonEventJson()).put("$", field, value).jsonString()
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(invalidRequestProblem())
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
			.andExpect(status().isBadRequest)
			.andExpect(invalidRequestProblem())
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
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(invalidRequestProblem())
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
			.andExpect(status().isBadRequest)
			.andExpect(invalidRequestProblem())
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
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(invalidRequestProblem())
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
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(invalidRequestProblem())
	}

	@ParameterizedTest
	@ValueSource(strings = ["abc", "2147483648"])
	fun `Spring rejects page sizes that cannot bind to an integer`(limit: String) {
		mockMvc.perform(get("/api/events").param("limit", limit))
			.andExpect(status().isBadRequest)
			.andExpect(invalidRequestProblem())
	}

	@Test
	fun `public event list rejects an invalid cursor`() {
		mockMvc.perform(get("/api/events").param("cursor", "%%%"))
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(invalidRequestProblem())
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
			.andExpect(status().isBadRequest)
			.andExpect(invalidRequestProblem())
	}

	private fun invalidRequestProblem() = ResultMatcher { result ->
		status().isBadRequest.match(result)
		content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result)
		jsonPath("$.status").value(400).match(result)
		// RFC 9457 defaults an omitted type to about:blank.
		jsonPath("$.title").isNotEmpty.match(result)
		jsonPath("$.instance").value(result.request.requestURI).match(result)
		jsonPath("$.errors").doesNotExist().match(result)
		assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Long::class.java))
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
