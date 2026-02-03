package com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.restapi.Headers
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.extensions.kotlin.asCommandMessage
import org.axonframework.extensions.kotlin.asEventMessage
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class IncreaseAvailableCreatures(
    @get:JvmName("getDwellingId")
    val dwellingId: DwellingId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    val increaseBy: Quantity,
)

private data class State(val isBuilt: Boolean, val availableCreatures: Quantity)

private val initialState = State(isBuilt = false, availableCreatures = Quantity.zero())

private fun decide(
    command: IncreaseAvailableCreatures,
    state: State
): DwellingEvent {
    if (!state.isBuilt) {
        throw IllegalStateException("Only built dwelling can have available creatures")
    }

    // todo: check creatureId for the dwelling!

    return AvailableCreaturesChanged(
        dwellingId = command.dwellingId,
        creatureId = command.creatureId,
        changedBy = command.increaseBy.raw,
        changedTo = state.availableCreatures + command.increaseBy
    )
}

private fun evolve(state: State, event: DwellingEvent): State = when (event) {
    is DwellingBuilt ->
        state.copy(isBuilt = true)

    is AvailableCreaturesChanged ->
        state.copy(availableCreatures = event.changedTo)

    else -> state
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.increaseavailablecreatures.enabled"])
@EventSourced(tagKey = EventTags.DWELLING_ID) // ConsistencyBoundary
private class IncreaseAvailableCreaturesEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = IncreaseAvailableCreaturesEventSourcedState(
        evolve(
            state,
            event
        )
    )

    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged) = IncreaseAvailableCreaturesEventSourcedState(
        evolve(
            state,
            event
        )
    )
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.increaseavailablecreatures.enabled"])
@Component
private class IncreaseAvailableCreaturesCommandHandler {

    @CommandHandler
    fun handle(
        command: IncreaseAvailableCreatures,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: IncreaseAvailableCreaturesEventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessage(metadata))
    }

}

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.increaseavailablecreatures.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class IncreaseAvailableCreaturesRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(val creatureId: String, val increaseBy: Int)

    @PutMapping("/dwellings/{dwellingId}/available-creatures-increases")
    fun putDwellings(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
        @RequestBody requestBody: Body
    ) {
        val command =
            IncreaseAvailableCreatures(
                DwellingId(dwellingId),
                CreatureId(requestBody.creatureId),
                Quantity(requestBody.increaseBy)
            )

        val gameId = GameId(gameId)
        val playerId = PlayerId(playerId)
        val metadata = GameMetadata.with(gameId, playerId)
        val message = command.asCommandMessage(metadata)

        commandGateway.sendAndWait(message)
    }
}





