package com.dddheroes.heroesofddd.calendar.write.startday

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.calendar.events.CalendarEvent
import com.dddheroes.heroesofddd.calendar.events.DayStarted
import com.dddheroes.heroesofddd.calendar.write.CalendarId
import com.dddheroes.heroesofddd.calendar.write.Day
import com.dddheroes.heroesofddd.calendar.write.Month
import com.dddheroes.heroesofddd.calendar.write.Week
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.application.resultOf
import com.dddheroes.heroesofddd.shared.application.toCommandResult
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.restapi.Headers
import com.dddheroes.heroesofddd.shared.restapi.toResponseEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.extensions.kotlin.asEventMessages
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class StartDay(
    @get:JvmName("getCalendarId")
    val calendarId: CalendarId,
    val month: Month,
    val week: Week,
    val day: Day,
)

private data class State(
    val currentMonth: Month?,
    val currentWeek: Week?,
    val currentDay: Day?,
) {
    companion object {
        val initialState = State(currentMonth = null, currentWeek = null, currentDay = null)
    }
}

private fun decide(command: StartDay, state: State): List<HeroesEvent> {
    if (state.currentDay == null || state.currentWeek == null || state.currentMonth == null) {
        return listOf(
            DayStarted(
                calendarId = command.calendarId,
                month = command.month,
                week = command.week,
                day = command.day
            )
        )
    }

    val currentDay = state.currentDay
    val currentWeek = state.currentWeek
    val currentMonth = state.currentMonth

    val expectedDay = currentDay.next()
    val weekRollover = currentDay.isLast
    val expectedWeek = if (weekRollover) currentWeek.next() else currentWeek
    val monthRollover = weekRollover && currentWeek.isLast
    val expectedMonth = if (monthRollover) currentMonth.next() else currentMonth

    if (command.day != expectedDay || command.week != expectedWeek || command.month != expectedMonth) {
        throw IllegalStateException("Cannot skip days")
    }

    return listOf(
        DayStarted(
            calendarId = command.calendarId,
            month = command.month,
            week = command.week,
            day = command.day
        )
    )
}

private fun evolve(state: State, event: CalendarEvent): State = when (event) {
    is DayStarted -> state.copy(
        currentMonth = event.month,
        currentWeek = event.week,
        currentDay = event.day
    )

    else -> state
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.calendar", name = ["write.startday.enabled"])
@EventSourced(tagKey = EventTags.CALENDAR_ID)
private class StartDayEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(State.initialState)

    @EventSourcingHandler
    fun evolve(event: DayStarted) = StartDayEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.calendar", name = ["write.startday.enabled"])
@Component
private class StartDayCommandHandler {

    @CommandHandler
    fun handle(
        command: StartDay,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.CALENDAR_ID) eventSourced: StartDayEventSourcedState,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metadata))
        events.toCommandResult()
    }

}

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.calendar", name = ["write.startday.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class StartDayRestApi(private val commandGateway: CommandGateway) {

    data class Body(val month: Int, val week: Int, val day: Int)

    @PostMapping("/calendars/{calendarId}/days")
    fun postStartDay(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable calendarId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = StartDay(
            calendarId = CalendarId(calendarId),
            month = Month(requestBody.month),
            week = Week(requestBody.week),
            day = Day(requestBody.day)
        )

        val gameId = GameId(gameId)
        val playerId = PlayerId(playerId)
        val metadata = GameMetadata.with(gameId, playerId)

        return commandGateway.send(command, metadata)
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
