package com.dddheroes.heroesofddd.scenario.write.recruitcreature

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.scenario.events.ArmyExpansionEvent
import com.dddheroes.heroesofddd.scenario.events.ArmyReductionEvent
import com.dddheroes.heroesofddd.scenario.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.scenario.events.CreatureRecruited
import com.dddheroes.heroesofddd.scenario.events.DwellingBuilt
import com.dddheroes.heroesofddd.scenario.events.WanderingCreaturesJoined
import com.dddheroes.heroesofddd.shared.application.GameMetaData
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.restapi.Headers
import org.axonframework.commandhandling.annotation.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.gateway.EventAppender
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.eventstreaming.EventCriteria
import org.axonframework.eventstreaming.Tag
import org.axonframework.extensions.kotlin.asCommandMessage
import org.axonframework.extensions.kotlin.asEventMessages
import org.axonframework.messaging.MetaData
import org.axonframework.modelling.annotation.InjectEntity
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule
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

private data class State(
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

    is ArmyExpansionEvent -> {
        val currentQuantity = state.creaturesInArmy[event.creatureId] ?: 0
        val updatedCreatures = state.creaturesInArmy + (event.creatureId to currentQuantity + event.quantity)
        state.copy(creaturesInArmy = updatedCreatures)
    }

    is ArmyReductionEvent -> {
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

@EventSourcedEntity // ConsistencyBoundary
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
    fun evolve(event: WanderingCreaturesJoined) = EventSourcedState(
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
                        CreatureRecruited::class.java.getName(),
                    ),
                EventCriteria
                    .havingTags(Tag.of(EventTags.ARMY_ID, recruitmentId.armyId))
                    .andBeingOneOfTypes(
                        WanderingCreaturesJoined::class.java.getName(),
                    )
            )
    }
}

private class RecruitCreatureCommandHandler {

    @CommandHandler
    fun handle(
        command: RecruitCreature,
        metaData: MetaData,
        @InjectEntity(idProperty = "recruitmentId") eventSourced: EventSourcedState,
        eventAppender: EventAppender
    ) {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metaData))
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
                    RecruitCreature.RecruitmentId::class.java,
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

        val metaData = GameMetaData.with(gameId, playerId)
        val message = command.asCommandMessage(metaData)

        commandGateway.sendAndWait(message)
    }
}

