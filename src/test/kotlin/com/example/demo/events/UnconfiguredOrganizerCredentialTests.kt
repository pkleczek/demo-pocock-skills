package com.example.demo.events

import com.example.demo.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(properties = ["organizer.api-key="])
class UnconfiguredOrganizerCredentialTests @Autowired constructor(
	private val mockMvc: MockMvc,
) {
	@Test
	fun `an unconfigured Organizer credential never authenticates a request`() {
		mockMvc.perform(post("/api/organizer/events"))
			.andExpect(status().isUnauthorized)
		mockMvc.perform(
			post("/api/organizer/events").header(HttpHeaders.AUTHORIZATION, "Bearer test-organizer-key"),
		)
			.andExpect(status().isUnauthorized)
	}

	@Test
	fun `public Event retrieval does not require an Organizer credential to be configured`() {
		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk)
	}
}
