package com.example.demo.events

import com.example.demo.events.outbound.jooq.Tables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JooqGeneratedSchemaTests {
	@Test
	fun `generated jOOQ schema reflects the Flyway Event table`() {
		val events = Tables.EVENTS

		assertEquals("events", events.name)
		assertNotNull(events.field("start_at"))
		assertNotNull(events.field("end_at"))
		assertNotNull(events.field("join_url"))
	}
}
