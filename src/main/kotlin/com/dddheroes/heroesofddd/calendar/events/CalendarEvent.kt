package com.dddheroes.heroesofddd.calendar.events

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.calendar.write.CalendarId
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import org.axonframework.eventsourcing.annotation.EventTag

sealed interface CalendarEvent : HeroesEvent {
    @get:EventTag(EventTags.CALENDAR_ID)
    val calendarId: CalendarId
}
