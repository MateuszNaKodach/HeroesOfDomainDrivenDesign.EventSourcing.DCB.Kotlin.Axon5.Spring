package com.dddheroes.heroesofddd.creaturerecruitment.write

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
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
import org.springframework.web.bind.annotation.*

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class BuildDwelling(
    val dwellingId: String,
    val creatureId: String,
    val costPerTroop: Map<String, Int>,
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

@EventSourcedEntity(tagKey = EventTags.DWELLING_ID) // ConsistencyBoundary
private class EventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingEvent) = EventSourcedState(evolve(state, event))
}

private class BuildDwellingCommandHandler {

    @CommandHandler
    fun handle(
        command: BuildDwelling,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: EventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events)
    }

}

@Configuration
private class BuildDwellingWriteSliceConfig {

    @Bean
    fun module(): StatefulCommandHandlingModule {
        val state = EventSourcedEntityModule.annotated(
            String::class.java,
            EventSourcedState::class.java
        )
        return StatefulCommandHandlingModule.named(BuildDwelling::class.simpleName)
            .entities()
            .entity(state)
            .commandHandlers()
            .annotatedCommandHandlingComponent { BuildDwellingCommandHandler() }
            .build()
    }
}

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

@RestController
@RequestMapping("games/{gameId}")
private class BuildDwellingRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(val creatureId: String, val costPerTroop: MutableMap<String, Int>)

    @PutMapping("/dwellings/{dwellingId}")
    fun putDwellings(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
        @RequestBody requestBody: Body
    ) {
        val command =
            BuildDwelling(
                dwellingId,
                requestBody.creatureId,
                requestBody.costPerTroop
            )
        commandGateway.sendAndWait(command) // todo: MetaData
    }
}

