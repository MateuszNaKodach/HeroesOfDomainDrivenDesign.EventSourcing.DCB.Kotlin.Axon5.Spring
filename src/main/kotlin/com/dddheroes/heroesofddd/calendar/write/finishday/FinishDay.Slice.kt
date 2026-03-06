package com.dddheroes.heroesofddd.calendar.write.finishday

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

data class FinishDay(
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
)

private val initialState = State(currentMonth = null, currentWeek = null, currentDay = null)

private fun decide(command: FinishDay, state: State): List<HeroesEvent> {
    if (state.currentDay == null || state.currentWeek == null || state.currentMonth == null) {
        throw IllegalStateException("Can only finish current day")
    }

    val isCurrentDay = command.month == state.currentMonth
            && command.week == state.currentWeek
            && command.day == state.currentDay

    if (!isCurrentDay) {
        throw IllegalStateException("Can only finish current day")
    }

    return listOf(
        DayFinished(
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

    is DayFinished -> state.copy(
        currentMonth = null,
        currentWeek = null,
        currentDay = null
    )
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.calendar", name = ["write.finishday.enabled"])
@EventSourced(tagKey = EventTags.CALENDAR_ID)
private class FinishDayEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DayStarted) = FinishDayEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: DayFinished) = FinishDayEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.calendar", name = ["write.finishday.enabled"])
@Component
private class FinishDayCommandHandler {

    @CommandHandler
    fun handle(
        command: FinishDay,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.CALENDAR_ID) eventSourced: FinishDayEventSourcedState,
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

@ConditionalOnProperty(prefix = "slices.calendar", name = ["write.finishday.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class FinishDayRestApi(private val commandGateway: CommandGateway) {

    data class Body(val month: Int, val week: Int, val day: Int)

    @PostMapping("/calendar/days/finish")
    fun postFinishDay(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = FinishDay(
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
