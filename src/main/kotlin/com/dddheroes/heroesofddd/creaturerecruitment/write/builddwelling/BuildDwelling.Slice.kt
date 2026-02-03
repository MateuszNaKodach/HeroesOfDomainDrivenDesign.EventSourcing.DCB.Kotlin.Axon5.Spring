package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.heroesofddd.shared.restapi.Headers
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.extensions.kotlin.asCommandMessage
import org.axonframework.extensions.kotlin.asEventMessages
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class BuildDwelling(
    @get:JvmName("getDwellingId")
    val dwellingId: DwellingId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    val costPerTroop: Resources,
)

private data class State(val isBuilt: Boolean)

private val initialState = State(isBuilt = false)

private fun decide(command: BuildDwelling, state: State): List<HeroesEvent> {
    if (state.isBuilt) {
        return emptyList()
    }

    return listOf(
        DwellingBuilt(
            dwellingId = command.dwellingId,
            creatureId = command.creatureId,
            costPerTroop = command.costPerTroop
        )
    )
}

private fun evolve(state: State, event: DwellingEvent): State {
    return when (event) {
        is DwellingBuilt -> state.copy(isBuilt = true)
        else -> state
    }
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@EventSourced(tagKey = EventTags.DWELLING_ID) // ConsistencyBoundary
private class BuildDwellingEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = BuildDwellingEventSourcedState(evolve(state, event))
}

@Component
private class BuildDwellingCommandHandler {

    @CommandHandler
    fun handle(
        command: BuildDwelling,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: BuildDwellingEventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metadata))
    }

}

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

@RestController
@RequestMapping("games/{gameId}")
private class BuildDwellingRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(val creatureId: String, val costPerTroop: Map<String, Int>)

    @PutMapping("/dwellings/{dwellingId}")
    fun putDwellings(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
        @RequestBody requestBody: Body
    ) {
        val command =
            BuildDwelling(
                DwellingId(dwellingId),
                CreatureId(requestBody.creatureId),
                Resources.of(requestBody.costPerTroop)
            )

        val gameId = GameId(gameId)
        val playerId = PlayerId(playerId)
        val metadata = GameMetadata.with(gameId, playerId)
        val message = command.asCommandMessage(metadata)

        commandGateway.sendAndWait(message)
    }
}

