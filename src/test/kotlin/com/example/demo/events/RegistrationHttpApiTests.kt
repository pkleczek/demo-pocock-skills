package com.example.demo.events

import com.example.demo.TestcontainersConfiguration
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(properties = ["organizer.api-key=test-organizer-key"])
class RegistrationHttpApiTests @Autowired constructor(
	private val mockMvc: MockMvc,
	private val jdbcTemplate: JdbcTemplate,
) {
	@BeforeEach
	fun clearEvents() {
		jdbcTemplate.execute("TRUNCATE events CASCADE")
	}

	@Test
	fun `a guest Registration is immediately confirmed and retrievable by the Organizer`() {
		val eventId = publishEvent()
		val response = mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.id").isNotEmpty)
			.andExpect(jsonPath("$.eventId").value(eventId))
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.fullName").doesNotExist())
			.andExpect(jsonPath("$.email").doesNotExist())
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
			.andReturn()
		val registrationId = JsonPath.read<String>(response.response.contentAsString, "$.id")

		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(registrationId))
			.andExpect(jsonPath("$.items[0].eventId").value(eventId))
			.andExpect(jsonPath("$.items[0].fullName").value("Ada Lovelace"))
			.andExpect(jsonPath("$.items[0].email").value("ada@example.test"))
			.andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
	}

	private fun publishEvent(online: Boolean = false): String {
		val attendance = if (online) {
			""""attendanceMode":"ONLINE","joinUrl":"https://meet.example.test/private-room""""
		} else {
			""""attendanceMode":"IN_PERSON","location":"Test venue""""
		}
		val response = mockMvc.perform(
			post("/api/organizer/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					"""
					{"title":"Multi-day Kotlin Event","startAt":"2027-05-10T16:00:00Z",
					 "endAt":"2027-05-12T18:00:00Z","timeZone":"Europe/Warsaw",
					 $attendance}
					""".trimIndent(),
				),
		)
			.andExpect(status().isCreated)
			.andReturn()
		return JsonPath.read(response.response.contentAsString, "$.id")
	}

	@Test
	fun `duplicate Registrations are rejected after trimming and case normalizing the email`() {
		val eventId = publishEvent()
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"  Ada@Example.TEST  "}"""),
		).andExpect(status().isCreated)

		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Another name","email":"ada@example.test"}"""),
		)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.title").value("Already registered"))
			.andExpect(jsonPath("$.fullName").doesNotExist())
			.andExpect(jsonPath("$.email").doesNotExist())

		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].fullName").value("Ada Lovelace"))
			.andExpect(jsonPath("$.items[0].email").value("ada@example.test"))
	}

	@Test
	fun `concurrent duplicate requests create exactly one active Registration`() {
		val eventId = publishEvent()
		val ready = CountDownLatch(8)
		val start = CountDownLatch(1)
		val statuses = Executors.newFixedThreadPool(8).use { executor ->
			val requests = (1..8).map {
				executor.submit<Int> {
					ready.countDown()
					check(start.await(10, TimeUnit.SECONDS))
					mockMvc.perform(
						post("/api/events/$eventId/registrations")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
					).andReturn().response.status
				}
			}
			try {
				assertTrue(ready.await(10, TimeUnit.SECONDS))
			} finally {
				start.countDown()
			}
			requests.map { it.get(30, TimeUnit.SECONDS) }
		}
		assertEquals(listOf(201, 409, 409, 409, 409, 409, 409, 409), statuses.sorted())
		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(1))
	}

	@Test
	fun `an online Event reveals its join URL only in a successful Registration response`() {
		val eventId = publishEvent(online = true)
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.joinUrl").value("https://meet.example.test/private-room"))
		mockMvc.perform(get("/api/events/$eventId"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items[0].joinUrl").doesNotExist())
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
		)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
	}

	@Test
	fun `cancelled Events cannot accept Registrations`() {
		val eventId = publishEvent(online = true)
		// Cancellation API belongs to ticket 05; seed its resulting state for this ticket's boundary.
		jdbcTemplate.update("UPDATE events SET status = 'CANCELLED' WHERE id = ?", UUID.fromString(eventId))
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
		)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.title").value("Event is not published"))
			.andExpect(jsonPath("$.joinUrl").doesNotExist())
		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))
	}

	@ParameterizedTest
	@ValueSource(strings = ["", " ", "ada", "ada@", "@example.test", "ada@@example.test", "ada lovelace@example.test", "ada..lovelace@example.test"])
	fun `invalid email addresses cannot create a Registration`(email: String) {
		val eventId = publishEvent()
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(JsonPath.parse("""{"fullName":"Ada Lovelace","email":""}""").set("$.email", email).jsonString()),
		)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.title").value("Invalid Registration details"))
			.andExpect(jsonPath("$.errors[0].field").value("email"))
		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))
	}

	@Test
	fun `Registration validation reports a blank name and an overlong email without persisting them`() {
		val eventId = publishEvent()
		val email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62)
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"  ","email":"$email"}"""),
		)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].field").value("fullName"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
			.andExpect(jsonPath("$.errors[1].field").value("email"))
			.andExpect(jsonPath("$.errors[1].message").value("must be at most 254 characters"))
		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))
	}

	@Test
	fun `unknown Events return not found for Registration and Organizer review`() {
		val eventId = "73cdd62f-0111-4fbe-8b82-0769afef08bd"
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
		).andExpect(status().isNotFound)
		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		).andExpect(status().isNotFound)
	}

	@Test
	fun `Registrations are scoped to their Event and distinct Attendees can register freely`() {
		val firstEvent = publishEvent()
		val secondEvent = publishEvent()
		for (eventId in listOf(firstEvent, secondEvent)) {
			mockMvc.perform(
				post("/api/events/$eventId/registrations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
			).andExpect(status().isCreated)
		}
		mockMvc.perform(
			post("/api/events/$firstEvent/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Grace Hopper","email":"grace@example.test"}"""),
		).andExpect(status().isCreated)

		for ((eventId, expectedCount) in listOf(firstEvent to 2, secondEvent to 1)) {
			mockMvc.perform(
				get("/api/organizer/events/$eventId/registrations")
					.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
			)
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.items.length()").value(expectedCount))
				.andExpect(jsonPath("$.items[0].eventId").value(eventId))
		}
	}

	@Test
	fun `Attendee details are available only in the authenticated Organizer review`() {
		val eventId = publishEvent()
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"ada@example.test"}"""),
		).andExpect(status().isCreated)

		for (credential in listOf(null, "Bearer wrong-organizer-key")) {
			val request = get("/api/organizer/events/$eventId/registrations")
			if (credential != null) request.header(HttpHeaders.AUTHORIZATION, credential)
			mockMvc.perform(request)
				.andExpect(status().isUnauthorized)
				.andExpect(content().string(not(containsString("Ada Lovelace"))))
				.andExpect(content().string(not(containsString("ada@example.test"))))
		}
		for (path in listOf("/api/events", "/api/events/$eventId")) {
			mockMvc.perform(get(path))
				.andExpect(status().isOk)
				.andExpect(content().string(not(containsString("Ada Lovelace"))))
				.andExpect(content().string(not(containsString("ada@example.test"))))
		}
		mockMvc.perform(get("/api/events/$eventId/registrations"))
			.andExpect(status().isMethodNotAllowed)
			.andExpect(content().string(not(containsString("Ada Lovelace"))))
			.andExpect(content().string(not(containsString("ada@example.test"))))
	}

	@ParameterizedTest
	@CsvSource(
		"fullName, missing, is required", "fullName, null, is required", "fullName, array, must be a string",
		"email, missing, is required", "email, null, is required", "email, array, must be a string",
	)
	fun `invalid Registration request shapes receive safe field errors`(field: String, shape: String, message: String) {
		val eventId = publishEvent()
		val json = JsonPath.parse("""{"fullName":"Ada Lovelace","email":"ada@example.test"}""")
		val body = when (shape) {
			"missing" -> json.delete("$.$field")
			"null" -> json.set("$.$field", null)
			else -> json.set("$.$field", listOf("private-invalid-value"))
		}.jsonString()
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		)
			.andExpect(status().isBadRequest)
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[0].field").value(field))
			.andExpect(jsonPath("$.errors[0].message").value(message))
			.andExpect(content().string(not(containsString("private-invalid-value"))))
		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items.length()").value(0))
	}

	@Test
	fun `names retain international characters and email accepts the 254 character boundary`() {
		val eventId = publishEvent()
		val email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61)
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"  Łucja O'Connell  ","email":"$email"}"""),
		).andExpect(status().isCreated)
		mockMvc.perform(
			get("/api/organizer/events/$eventId/registrations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.items[0].fullName").value("Łucja O'Connell"))
			.andExpect(jsonPath("$.items[0].email").value(email))
	}

	@Test
	fun `very long dotted email addresses return a client error rather than exhausting validation`() {
		val eventId = publishEvent()
		val email = "a.".repeat(5_000) + "a@example.test"
		mockMvc.perform(
			post("/api/events/$eventId/registrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"fullName":"Ada Lovelace","email":"$email"}"""),
		)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.errors[0].field").value("email"))
			.andExpect(jsonPath("$.errors[0].message").value("must be at most 254 characters"))
	}
}
