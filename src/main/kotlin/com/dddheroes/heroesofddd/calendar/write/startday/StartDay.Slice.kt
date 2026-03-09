package com.dddheroes.heroesofddd.calendar.write.startday

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.calendar.events.CalendarEvent
import com.dddheroes.heroesofddd.calendar.events.DayFinished
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
import org.axonframework.messaging.commandhandling.annotation.Command
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

@Command(namespace = "Calendar", name = "StartDay", version = "1.0.0")
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
    val lastMonth: Month?,
    val lastWeek: Week?,
    val lastDay: Day?,
)

private val initialState = State(
    currentMonth = null, currentWeek = null, currentDay = null,
    lastMonth = null, lastWeek = null, lastDay = null
)

private fun decide(command: StartDay, state: State): List<HeroesEvent> {
    if (state.lastDay == null || state.lastWeek == null || state.lastMonth == null) {
        return listOf(
            DayStarted(
                calendarId = command.calendarId,
                month = command.month,
                week = command.week,
                day = command.day
            )
        )
    }

    val lastDay = state.lastDay
    val lastWeek = state.lastWeek
    val lastMonth = state.lastMonth

    val expectedDay = lastDay.next()
    val weekRollover = lastDay.isLast
    val expectedWeek = if (weekRollover) lastWeek.next() else lastWeek
    val monthRollover = weekRollover && lastWeek.isLast
    val expectedMonth = if (monthRollover) lastMonth.next() else lastMonth

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
        currentDay = event.day,
        lastMonth = event.month,
        lastWeek = event.week,
        lastDay = event.day
    )

    is DayFinished -> state.copy(
        currentMonth = null,
        currentWeek = null,
        currentDay = null
    )
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.calendar", name = ["write.startday.enabled"])
@EventSourced(tagKey = EventTags.CALENDAR_ID)
private class StartDayEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DayStarted) = StartDayEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: DayFinished) = StartDayEventSourcedState(evolve(state, event))
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
        eventAppender.append(events, metadata)
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

    @PostMapping("/calendar/days")
    fun postStartDay(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = StartDay(
            calendarId = CalendarId(gameId),
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
