package com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling.BuildDwelling
import com.dddheroes.heroesofddd.shared.restapi.Headers
import org.axonframework.commandhandling.annotation.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.modelling.annotation.InjectEntity
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class IncreaseAvailableCreatures(
    val dwellingId: String,
    val creatureId: String,
    val increaseBy: Int,
)

private data class State(val isBuilt: Boolean, val availableCreatures: Int)

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
private class EventSourcedState private constructor(val state: State) {

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
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: EventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events)
    }

}


@Configuration
internal class IncreaseAvailableCreaturesWriteSliceConfig {

    @Bean
    fun increaseAvailableCreaturesSlice(): StatefulCommandHandlingModule =
        StatefulCommandHandlingModule.named(BuildDwelling::class.simpleName)
            .entities()
            .entity(
                EventSourcedEntityModule.annotated(
                    String::class.java,
                    EventSourcedState::class.java
                )
            )
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
        commandGateway.sendAndWait(command) // todo: MetaData
    }
}





