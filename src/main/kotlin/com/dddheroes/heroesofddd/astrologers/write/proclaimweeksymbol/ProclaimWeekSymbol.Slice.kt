package com.dddheroes.heroesofddd.astrologers.write.proclaimweeksymbol

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.astrologers.events.AstrologersEvent
import com.dddheroes.heroesofddd.astrologers.events.WeekSymbolProclaimed
import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.astrologers.write.MonthWeek
import com.dddheroes.heroesofddd.astrologers.write.WeekSymbol
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.application.resultOf
import com.dddheroes.heroesofddd.shared.application.toCommandResult
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
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

data class ProclaimWeekSymbol(
    @get:JvmName("getAstrologersId")
    val astrologersId: AstrologersId,
    val week: MonthWeek,
    val symbol: WeekSymbol,
)

private data class State(val lastProclaimed: MonthWeek?)

private val initialState = State(lastProclaimed = null)

private fun decide(command: ProclaimWeekSymbol, state: State): List<HeroesEvent> {
    val lastProclaimed = state.lastProclaimed
    if (lastProclaimed != null && lastProclaimed.weekNumber >= command.week.weekNumber) {
        throw IllegalStateException("Only one symbol can be proclaimed per week")
    }

    return listOf(
        WeekSymbolProclaimed(
            astrologersId = command.astrologersId,
            month = command.week.month,
            week = command.week.week,
            weekOf = command.symbol.weekOf,
            growth = command.symbol.growth
        )
    )
}

private fun evolve(state: State, event: AstrologersEvent): State = when (event) {
    is WeekSymbolProclaimed -> state.copy(lastProclaimed = MonthWeek(event.month, event.week))
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.astrologers", name = ["write.proclaimweeksymbol.enabled"])
@EventSourced(tagKey = EventTags.ASTROLOGERS_ID) // ConsistencyBoundary
private class ProclaimWeekSymbolEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: WeekSymbolProclaimed) = ProclaimWeekSymbolEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.astrologers", name = ["write.proclaimweeksymbol.enabled"])
@Component
private class ProclaimWeekSymbolCommandHandler {

    @CommandHandler
    fun handle(
        command: ProclaimWeekSymbol,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.ASTROLOGERS_ID) eventSourced: ProclaimWeekSymbolEventSourcedState,
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

@ConditionalOnProperty(prefix = "slices.astrologers", name = ["write.proclaimweeksymbol.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class ProclaimWeekSymbolRestApi(private val commandGateway: CommandGateway) {
    data class Body(val month: Int, val week: Int, val creatureId: String, val growth: Int)

    @PutMapping("/astrologers/{astrologersId}/week-symbol-proclamations")
    fun putWeekSymbolProclamation(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable astrologersId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = ProclaimWeekSymbol(
            astrologersId = AstrologersId(astrologersId),
            week = MonthWeek(requestBody.month, requestBody.week),
            symbol = WeekSymbol(CreatureId(requestBody.creatureId), requestBody.growth)
        )

        val gameId = GameId(gameId)
        val playerId = PlayerId(playerId)
        val metadata = GameMetadata.with(gameId, playerId)

        return commandGateway.send(command, metadata)
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
