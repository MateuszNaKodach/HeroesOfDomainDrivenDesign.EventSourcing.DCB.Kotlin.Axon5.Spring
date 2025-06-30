package com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruited
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
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

data class RecruitCreature(
    val dwellingId: String,
    val creatureId: String,
    val armyId: String,
    val quantity: Int,
    val expectedCost: Map<String, Int>,
)

private data class State(
    val creatureId: String,
    val availableCreatures: Int,
    val costPerTroop: Map<String, Int>
)

private val initialState = State(
    creatureId = "",
    availableCreatures = 0,
    costPerTroop = emptyMap()
)

private fun multiplyCost(cost: Map<String, Int>, multiplier: Int): Map<String, Int> {
    return cost.mapValues { (_, value) -> value * multiplier }
}

private fun isSameCost(cost1: Map<String, Int>, cost2: Map<String, Int>): Boolean {
    return cost1 == cost2
}

private fun decide(
    command: RecruitCreature,
    state: State
): List<DwellingEvent> {
    if (state.creatureId != command.creatureId || state.availableCreatures < command.quantity) {
        throw IllegalStateException("Recruit creatures cannot exceed available creatures")
    }

    val recruitCost = multiplyCost(state.costPerTroop, command.quantity)
    if (!isSameCost(command.expectedCost, recruitCost)) {
        throw IllegalStateException("Recruit cost cannot differ than expected cost")
    }

    return listOf(
        CreatureRecruited(
            dwellingId = command.dwellingId,
            creatureId = command.creatureId,
            toArmy = command.armyId,
            quantity = command.quantity,
            totalCost = recruitCost
        ),
        AvailableCreaturesChanged(
            dwellingId = command.dwellingId,
            creatureId = command.creatureId,
            changedBy = -command.quantity,
            changedTo = state.availableCreatures - command.quantity
        )
    )
}

private fun evolve(state: State, event: DwellingEvent): State = when (event) {
    is DwellingBuilt ->
        state.copy(
            creatureId = event.creatureId,
            costPerTroop = event.costPerTroop
        )

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
        evolve(state, event)
    )

    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged) = EventSourcedState(
        evolve(state, event)
    )

    @EventSourcingHandler
    fun evolve(event: CreatureRecruited) = EventSourcedState(
        evolve(state, event)
    )
}

private class RecruitCreatureCommandHandler {

    @CommandHandler
    fun handle(
        command: RecruitCreature,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: EventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events)
    }
}

@Configuration
internal class RecruitCreatureWriteSliceConfig {

    @Bean
    fun recruitCreatureSlice(): StatefulCommandHandlingModule =
        StatefulCommandHandlingModule.named(RecruitCreature::class.simpleName)
            .entities()
            .entity(
                EventSourcedEntityModule.annotated(
                    String::class.java,
                    EventSourcedState::class.java
                )
            )
            .commandHandlers()
            .annotatedCommandHandlingComponent { RecruitCreatureCommandHandler() }
            .build()
}

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

@RestController
@RequestMapping("games/{gameId}")
private class RecruitCreatureRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(
        val creatureId: String,
        val armyId: String,
        val quantity: Int,
        val expectedCost: Map<String, Int>
    )

    @PutMapping("/dwellings/{dwellingId}/creature-recruitments")
    fun putCreatureRecruitments(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
        @RequestBody requestBody: Body
    ) {
        val command = RecruitCreature(
            dwellingId = dwellingId,
            creatureId = requestBody.creatureId,
            armyId = requestBody.armyId,
            quantity = requestBody.quantity,
            expectedCost = requestBody.expectedCost
        )
        commandGateway.sendAndWait(command) // todo: MetaData
    }
}

