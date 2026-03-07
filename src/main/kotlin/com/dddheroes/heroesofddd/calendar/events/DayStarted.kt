package com.dddheroes.heroesofddd.calendar.events

import com.dddheroes.heroesofddd.calendar.write.CalendarId
import com.dddheroes.heroesofddd.calendar.write.Day
import com.dddheroes.heroesofddd.calendar.write.Month
import com.dddheroes.heroesofddd.calendar.write.Week
import org.axonframework.messaging.eventhandling.annotation.Event

@Event(namespace = "Calendar", name = "DayStarted", version = "1.0.0")
data class DayStarted(
    override val calendarId: CalendarId,
    val month: Month,
    val week: Week,
    val day: Day
) : CalendarEvent
