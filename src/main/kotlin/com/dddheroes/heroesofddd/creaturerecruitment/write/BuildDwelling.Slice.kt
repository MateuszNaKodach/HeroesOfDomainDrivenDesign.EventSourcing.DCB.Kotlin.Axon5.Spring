package com.dddheroes.heroesofddd.creaturerecruitment.write

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import org.axonframework.commandhandling.annotation.CommandHandler
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.stereotype.Component

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class BuildDwelling(
    val dwellingId: String,
    val creatureId: String,
    val costPerTroop: Map<String, Integer>,
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
private class EventSourcedState(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingEvent) = EventSourcedState(evolve(state, event))
}

@Component
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

