package com.dddheroes.heroesofddd.calendar.events

import com.dddheroes.heroesofddd.calendar.write.CalendarId
import com.dddheroes.heroesofddd.calendar.write.Day
import com.dddheroes.heroesofddd.calendar.write.Month
import com.dddheroes.heroesofddd.calendar.write.Week

data class DayFinished(
    override val calendarId: CalendarId,
    val month: Month,
    val week: Week,
    val day: Day
) : CalendarEvent
