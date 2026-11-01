package com.davipeter.iana.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Agenda do próprio celular via CalendarContract. Lê e grava nas mesmas agendas que o app
 * Google Agenda usa, e a sincronização com a nuvem é feita pelo Android.
 */
class AndroidCalendarSource(
    private val resolver: ContentResolver,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : CalendarSource {

    override suspend fun events(from: LocalDateTime, to: LocalDateTime): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val zone = zone()
        val fromMillis = from.atZone(zone).toInstant().toEpochMilli()
        val toMillis = to.atZone(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis)
            ContentUris.appendId(it, toMillis)
        }.build()
        val projection = arrayOf(Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.CALENDAR_DISPLAY_NAME)

        val result = mutableListOf<CalendarEvent>()
        resolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(3) == 1
                // Eventos de dia inteiro são gravados em UTC; os demais, no fuso do celular.
                val eventZone = if (allDay) ZoneOffset.UTC else zone
                val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), eventZone)
                val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(2)), eventZone)
                // Por causa do UTC, um evento de dia inteiro de amanhã pode "vazar" para hoje; filtramos.
                if (allDay && (start.toLocalDate() < from.toLocalDate() || !start.isBefore(to))) continue
                result += CalendarEvent(
                    title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(sem título)",
                    start = start,
                    end = end,
                    allDay = allDay,
                    calendarName = c.getString(4),
                )
            }
        }
        result
    }

    override suspend fun create(event: NewEvent): CreatedEvent = withContext(Dispatchers.IO) {
        val target = pickWritableCalendar() ?: throw NoWritableCalendarException()
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, target.id)
            put(Events.TITLE, event.title)
            if (event.time == null) {
                val start = event.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, start)
                put(Events.DTEND, event.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            } else {
                val zone = zone()
                val start = event.date.atTime(event.time).atZone(zone)
                put(Events.EVENT_TIMEZONE, zone.id)
                put(Events.DTSTART, start.toInstant().toEpochMilli())
                put(Events.DTEND, start.plusMinutes(event.durationMinutes.toLong()).toInstant().toEpochMilli())
            }
        }
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: error("O Android não gravou o evento")
        CreatedEvent(ContentUris.parseId(uri), target.name)
    }

    private data class WritableCalendar(val id: Long, val name: String, val primary: Boolean, val google: Boolean)

    /** Prefere a agenda principal de uma conta Google; senão, qualquer agenda em que dê pra gravar. */
    private fun pickWritableCalendar(): WritableCalendar? {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.IS_PRIMARY, Calendars.ACCOUNT_TYPE)
        val selection = "${Calendars.VISIBLE} = 1 AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR}"
        val calendars = mutableListOf<WritableCalendar>()
        resolver.query(Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                calendars += WritableCalendar(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Agenda",
                    primary = c.getInt(2) == 1,
                    google = c.getString(3) == "com.google",
                )
            }
        }
        return calendars.maxByOrNull { (if (it.primary) 2 else 0) + (if (it.google) 1 else 0) }
    }
}
