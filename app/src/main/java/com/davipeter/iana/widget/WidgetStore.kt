package com.davipeter.iana.widget

import android.content.Context
import java.time.LocalDateTime

/** Guarda o [WidgetSnapshot] em SharedPreferences. */
class WidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun load(): WidgetSnapshot = WidgetSnapshot(
        headlines = prefs.getString(KEY_HEADLINES, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
        nextEventTitle = prefs.getString(KEY_NEXT_TITLE, null),
        nextEventWhen = prefs.getString(KEY_NEXT_WHEN, null),
        calendarAllowed = prefs.getBoolean(KEY_CALENDAR_ALLOWED, true),
        updatedAt = prefs.getString(KEY_UPDATED_AT, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
    )

    fun save(snapshot: WidgetSnapshot) {
        prefs.edit()
            .putString(KEY_HEADLINES, snapshot.headlines.joinToString(SEPARATOR) { it.replace(SEPARATOR, " ") })
            .putString(KEY_NEXT_TITLE, snapshot.nextEventTitle)
            .putString(KEY_NEXT_WHEN, snapshot.nextEventWhen)
            .putBoolean(KEY_CALENDAR_ALLOWED, snapshot.calendarAllowed)
            .putString(KEY_UPDATED_AT, snapshot.updatedAt?.toString())
            .apply()
    }

    private companion object {
        const val SEPARATOR = "\n"
        const val KEY_HEADLINES = "headlines"
        const val KEY_NEXT_TITLE = "next_title"
        const val KEY_NEXT_WHEN = "next_when"
        const val KEY_CALENDAR_ALLOWED = "calendar_allowed"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
