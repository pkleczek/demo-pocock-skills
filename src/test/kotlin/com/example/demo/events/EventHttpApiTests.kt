package com.example.demo.events

import com.jayway.jsonpath.JsonPath
import com.example.demo.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(properties = ["organizer.api-key=test-organizer-key"])
class EventHttpApiTests @Autowired constructor(
	private val mockMvc: MockMvc,
	private val jdbcTemplate: JdbcTemplate,
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

	@Test
	fun `Organizer receives a clear validation error when the Event ends before it starts`() {
		mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "Backwards Event",
					  "startAt": "2027-10-12T18:00:00Z",
					  "endAt": "2027-10-12T17:00:00Z",
					  "timeZone": "America/Los_Angeles",
					  "attendanceMode": "IN_PERSON",
					  "location": "Test location"
					}
					""".trimIndent(),
				),
		)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors[0].field").value("endAt"))
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
			.andExpect(jsonPath("$.errors[0].field").value("timeZone"))
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
			.andExpect(jsonPath("$.errors[0].field").value("timeZone"))
	}

	@Test
	fun `Organizer cannot create a hybrid Event`() {
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
					  "attendanceMode": "IN_PERSON",
					  "location": "Test venue",
					  "joinUrl": "https://meet.example.test/hybrid-room"
					}
					""".trimIndent(),
				),
		)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors[0].field").value("joinUrl"))
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
					  "startAt": "2027-06-12T15:00:00Z",
					  "endAt": "2027-06-12T17:00:00Z",
					  "timeZone": "America/Los_Angeles",
					  "attendanceMode": "IN_PERSON",
					  "location": "Rose Garden, Portland"
					}
					""".trimIndent(),
			)
		).andExpect(status().isCreated)
			.andReturn()

		val eventId = createResponse.response.getHeader(HttpHeaders.LOCATION)!!
			.substringAfterLast('/')

		mockMvc.perform(get("/api/events/$eventId"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.id").value(eventId))
			.andExpect(jsonPath("$.title").value("Accessible Public Event"))
			.andExpect(jsonPath("$.startAt").value("2027-06-12T15:00:00Z"))
			.andExpect(jsonPath("$.endAt").value("2027-06-12T17:00:00Z"))
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

	@Test
	fun `public event list rejects a page size above the supported maximum`() {
		mockMvc.perform(get("/api/events").param("limit", "101"))
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors[0].field").value("limit"))
	}

	@Test
	fun `public event list rejects an invalid cursor`() {
		mockMvc.perform(get("/api/events").param("cursor", "not-a-cursor"))
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors[0].field").value("cursor"))
	}

	private fun publishInPersonEvent(title: String, startAt: String, endAt: String): String {
		val response = mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{
					  "title": "$title",
					  "startAt": "$startAt",
					  "endAt": "$endAt",
					  "timeZone": "America/Los_Angeles",
					  "attendanceMode": "IN_PERSON",
					  "location": "Test location"
					}
					""".trimIndent(),
				),
		)
			.andExpect(status().isCreated)
			.andReturn()
		return response.response.getHeader(HttpHeaders.LOCATION)!!
			.substringAfterLast('/')
	}
}
