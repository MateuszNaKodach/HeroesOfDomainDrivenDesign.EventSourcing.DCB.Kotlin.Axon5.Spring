package com.dddheroes.heroesofddd.calendar.events

import com.dddheroes.heroesofddd.calendar.write.CalendarId

data class DayFinished(
    override val calendarId: CalendarId,
    val month: Int,
    val week: Int,
    val day: Int
) : CalendarEvent
