package com.example.demo.events.outbound

import com.example.demo.events.application.EventCursor
import com.example.demo.events.application.EventQueries
import com.example.demo.events.domain.EventRepository
import com.example.demo.events.domain.AttendanceMode
import com.example.demo.events.domain.Event
import com.example.demo.events.domain.EventAttendance
import com.example.demo.events.domain.EventStatus
import com.example.demo.events.domain.EventTitle
import com.example.demo.events.outbound.jooq.Tables.EVENTS
import com.example.demo.events.outbound.jooq.tables.records.EventsRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.net.URI
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqEventRepository(
	private val dsl: DSLContext,
) : EventRepository, EventQueries {
	override fun save(event: Event) {
		dsl.insertInto(EVENTS)
			.set(EVENTS.ID, event.id)
			.set(EVENTS.TITLE, event.title.value)
			.set(EVENTS.DESCRIPTION, event.description)
			.set(EVENTS.START_AT, event.startAt.atOffset(ZoneOffset.UTC))
			.set(EVENTS.END_AT, event.endAt.atOffset(ZoneOffset.UTC))
			.set(EVENTS.TIME_ZONE, event.timeZone.id)
			.set(EVENTS.ATTENDANCE_MODE, event.attendanceMode.name)
			.set(EVENTS.LOCATION, (event.attendance as? EventAttendance.InPerson)?.location)
			.set(EVENTS.JOIN_URL, (event.attendance as? EventAttendance.Online)?.joinUrl?.toString())
			.set(EVENTS.STATUS, event.status.name)
			.execute()
	}

	override fun findById(id: UUID): Event? = dsl.selectFrom(EVENTS)
		.where(EVENTS.ID.eq(id))
		.fetchOne()
		?.let(::toEvent)

	override fun findPublishedAfter(cursor: EventCursor?, limit: Int): List<Event> {
		val query = dsl.selectFrom(EVENTS)
			.where(EVENTS.STATUS.eq(EventStatus.PUBLISHED.name))

		if (cursor != null) {
			val cursorStartAt = cursor.startAt.atOffset(ZoneOffset.UTC)
			query.and(
				EVENTS.START_AT.gt(cursorStartAt)
					.or(EVENTS.START_AT.eq(cursorStartAt).and(EVENTS.ID.gt(cursor.id))),
			)
		}

		return query
			.orderBy(EVENTS.START_AT.asc(), EVENTS.ID.asc())
			.limit(limit)
			.fetch(::toEvent)
	}

	private fun toEvent(record: EventsRecord): Event {
		val attendanceMode = AttendanceMode.valueOf(record.attendanceMode)
		val attendance = when (attendanceMode) {
			AttendanceMode.IN_PERSON -> EventAttendance.InPerson(requireNotNull(record.location))
			AttendanceMode.ONLINE -> EventAttendance.Online(URI.create(requireNotNull(record.joinUrl)))
		}
		val title = EventTitle(record.title)
		return Event.reconstitute(
			id = record.id,
			title = title,
			description = record.description,
			startAt = record.startAt.toInstant(),
			endAt = record.endAt.toInstant(),
			timeZone = ZoneId.of(record.timeZone),
			attendance = attendance,
			status = EventStatus.valueOf(record.status),
		)
	}
}
