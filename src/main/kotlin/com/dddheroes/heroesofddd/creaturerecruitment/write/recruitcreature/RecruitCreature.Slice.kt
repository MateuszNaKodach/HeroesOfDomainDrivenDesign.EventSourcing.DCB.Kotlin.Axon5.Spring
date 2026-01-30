package com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruited
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.restapi.Headers
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.extensions.kotlin.asCommandMessage
import org.axonframework.extensions.kotlin.asEventMessages
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.messaging.eventstreaming.EventCriteria
import org.axonframework.messaging.eventstreaming.Tag
import org.axonframework.modelling.annotation.InjectEntity
import org.axonframework.modelling.configuration.EntityModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.*

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class RecruitCreature(
    val dwellingId: String,
    val creatureId: String,
    val armyId: String,
    val quantity: Int,
    val expectedCost: Map<ResourceType, Int>,
){
    data class RecruitmentId(val dwellingId: String, val armyId: String)

    // used as a process identifier
    val recruitmentId = RecruitmentId(dwellingId, armyId)
}

internal data class State(
    val creatureId: String,
    val availableCreatures: Int,
    val costPerTroop: Map<ResourceType, Int>,
    val creaturesInArmy: Map<String, Int>
)

private val initialState = State(
    creatureId = "",
    availableCreatures = 0,
    costPerTroop = emptyMap(),
    creaturesInArmy = emptyMap()
)

private fun multiplyCost(cost: Map<ResourceType, Int>, multiplier: Int): Map<ResourceType, Int> {
    return cost.mapValues { (_, value) -> value * multiplier }
}

private fun isSameCost(cost1: Map<ResourceType, Int>, cost2: Map<ResourceType, Int>): Boolean {
    return cost1 == cost2
}

private fun decide(
    command: RecruitCreature,
    state: State
): List<HeroesEvent> {
    if (state.creatureId != command.creatureId || state.availableCreatures < command.quantity) {
        throw IllegalStateException("Recruit creatures cannot exceed available creatures")
    }

    val recruitCost = multiplyCost(state.costPerTroop, command.quantity)
    if (!isSameCost(command.expectedCost, recruitCost)) {
        throw IllegalStateException("Recruit cost cannot differ than expected cost")
    }

    if (!state.creaturesInArmy.containsKey(command.creatureId) && state.creaturesInArmy.size >= 7) {
        throw IllegalStateException("Army cannot contain more than 7 different creature types")
    }

    return listOf(
        CreatureRecruited(
            dwellingId = command.dwellingId,
            creatureId = command.creatureId,
            toArmy = command.armyId,
            quantity = command.quantity,
            totalCost = recruitCost
        ),
        CreatureAddedToArmy(
            armyId = command.armyId,
            creatureId = command.creatureId,
            quantity = command.quantity
        ),
        AvailableCreaturesChanged(
            dwellingId = command.dwellingId,
            creatureId = command.creatureId,
            changedBy = -command.quantity,
            changedTo = state.availableCreatures - command.quantity
        )
    )
}

private fun evolve(state: State, event: HeroesEvent): State = when (event) {
    is DwellingBuilt ->
        state.copy(
            creatureId = event.creatureId,
            costPerTroop = event.costPerTroop
        )

    is AvailableCreaturesChanged ->
        state.copy(availableCreatures = event.changedTo)

    is CreatureAddedToArmy -> {
        val currentQuantity = state.creaturesInArmy[event.creatureId] ?: 0
        val updatedCreatures = state.creaturesInArmy + (event.creatureId to currentQuantity + event.quantity)
        state.copy(creaturesInArmy = updatedCreatures)
    }

    is CreatureRemovedFromArmy -> {
        val currentQuantity = state.creaturesInArmy[event.creatureId] ?: 0
        val newQuantity = (currentQuantity - event.quantity).coerceAtLeast(0)
        val updatedCreatures = if (newQuantity == 0) {
            state.creaturesInArmy - event.creatureId
        } else {
            state.creaturesInArmy + (event.creatureId to newQuantity)
        }
        state.copy(creaturesInArmy = updatedCreatures)
    }

    else -> state
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@EventSourced // ConsistencyBoundary
internal class RecruitCreatureEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = RecruitCreatureEventSourcedState(
        evolve(state, event)
    )

    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged) = RecruitCreatureEventSourcedState(
        evolve(state, event)
    )

    @EventSourcingHandler
    fun evolve(event: CreatureAddedToArmy) = RecruitCreatureEventSourcedState(
        evolve(state, event)
    )

    @EventSourcingHandler
    fun evolve(event: CreatureRemovedFromArmy) = RecruitCreatureEventSourcedState(
        evolve(state, event)
    )

    companion object {
        @JvmStatic
        @EventCriteriaBuilder
        fun resolveCriteria(recruitmentId: RecruitCreature.RecruitmentId) =
            EventCriteria.either(
                EventCriteria
                    .havingTags(Tag.of(EventTags.DWELLING_ID, recruitmentId.dwellingId))
                    .andBeingOneOfTypes(
                        DwellingBuilt::class.java.getName(),
                        AvailableCreaturesChanged::class.java.getName(),
                    ),
                EventCriteria
                    .havingTags(Tag.of(EventTags.ARMY_ID, recruitmentId.armyId))
                    .andBeingOneOfTypes(
                        CreatureAddedToArmy::class.java.getName(),
                        CreatureRemovedFromArmy::class.java.getName(),
                    )
            )
    }
}


private class RecruitCreatureCommandHandler {

    @CommandHandler
    fun handle(
        command: RecruitCreature,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = "recruitmentId") eventSourced: RecruitCreatureEventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metadata))
    }
}

@Configuration
internal class RecruitCreatureWriteSliceConfig {

    @Bean
    fun recruitCreatureSliceState(): EntityModule<RecruitCreature.RecruitmentId, RecruitCreatureEventSourcedState>
    = EventSourcedEntityModule.autodetected(
        RecruitCreature.RecruitmentId::class.java,
        RecruitCreatureEventSourcedState::class.java
    )

    @Bean
    fun recruitCreatureSlice(): CommandHandlingModule =
        CommandHandlingModule.named(RecruitCreature::class.simpleName!!)
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
            expectedCost = requestBody.expectedCost.mapKeys { ResourceType.from(it.key) }
        )

        val metadata = GameMetadata.with(gameId, playerId)
        val message = command.asCommandMessage(metadata)

        commandGateway.sendAndWait(message)
    }
}

