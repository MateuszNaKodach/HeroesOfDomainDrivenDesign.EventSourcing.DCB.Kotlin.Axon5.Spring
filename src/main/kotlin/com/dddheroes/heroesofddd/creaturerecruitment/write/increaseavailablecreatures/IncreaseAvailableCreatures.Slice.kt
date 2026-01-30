package com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.restapi.Headers
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.extensions.kotlin.asCommandMessage
import org.axonframework.extensions.kotlin.asEventMessage
import org.axonframework.modelling.annotation.InjectEntity
import org.axonframework.modelling.configuration.EntityModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.*

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class IncreaseAvailableCreatures(
    val dwellingId: String,
    val creatureId: String,
    val increaseBy: Int,
)

internal data class State(val isBuilt: Boolean, val availableCreatures: Int)

private val initialState = State(isBuilt = false, availableCreatures = 0)

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
        changedBy = command.increaseBy,
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

@EventSourcedEntity(tagKey = EventTags.DWELLING_ID) // ConsistencyBoundary
internal class EventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = EventSourcedState(
        evolve(
            state,
            event
        )
    )

    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged) = EventSourcedState(
        evolve(
            state,
            event
        )
    )
}

private class IncreaseAvailableCreaturesCommandHandler {

    @CommandHandler
    fun handle(
        command: IncreaseAvailableCreatures,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: EventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessage(metadata))
    }

}


@Configuration
internal class IncreaseAvailableCreaturesWriteSliceConfig {

    @Bean
    fun increaseAvailableCreaturesSliceState(): EntityModule<String, EventSourcedState> =
        EventSourcedEntityModule.autodetected(
            String::class.java,
            EventSourcedState::class.java
        )

    @Bean
    fun increaseAvailableCreaturesSlice(): CommandHandlingModule =
        CommandHandlingModule.named(IncreaseAvailableCreatures::class.simpleName!!)
            .commandHandlers()
            .annotatedCommandHandlingComponent { IncreaseAvailableCreaturesCommandHandler() }
            .build()
}


////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

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
                dwellingId,
                requestBody.creatureId,
                requestBody.increaseBy
            )

        val metadata = GameMetadata.with(gameId, playerId)
        val message = command.asCommandMessage(metadata)

        commandGateway.sendAndWait(message)
    }
}





