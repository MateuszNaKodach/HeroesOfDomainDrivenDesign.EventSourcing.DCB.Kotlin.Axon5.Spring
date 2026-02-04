package com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruited
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.application.resultOf
import com.dddheroes.heroesofddd.shared.application.toCommandResult
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.*
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.heroesofddd.shared.restapi.Headers
import com.dddheroes.heroesofddd.shared.restapi.toResponseEntity
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class RecruitCreature(
    @get:JvmName("getDwellingId")
    val dwellingId: DwellingId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    val armyId: ArmyId,
    val quantity: Quantity,
    val expectedCost: Resources,
) {
    data class RecruitmentId(val dwellingId: DwellingId, val armyId: ArmyId)

    // used as a process identifier
    val recruitmentId = RecruitmentId(dwellingId, armyId)
}

private data class State(
    val creatureId: CreatureId,
    val availableCreatures: Quantity,
    val costPerTroop: Resources,
    val creaturesInArmy: Map<CreatureId, Quantity>
)

private val initialState = State(
    creatureId = CreatureId("undefined"),
    availableCreatures = Quantity.zero(),
    costPerTroop = Resources.empty(),
    creaturesInArmy = emptyMap()
)


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

    val recruitCost = state.costPerTroop * command.quantity
    if (command.expectedCost != recruitCost) {
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
            changedBy = -command.quantity.raw,
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
        val currentQuantity = state.creaturesInArmy[event.creatureId] ?: Quantity.zero()
        val updatedCreatures = state.creaturesInArmy + (event.creatureId to currentQuantity + event.quantity)
        state.copy(creaturesInArmy = updatedCreatures)
    }

    is CreatureRemovedFromArmy -> {
        val currentQuantity = state.creaturesInArmy[event.creatureId] ?: Quantity.zero()
        val newQuantity = currentQuantity - event.quantity
        val updatedCreatures = if (newQuantity == Quantity.zero()) {
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
private class RecruitCreatureEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt): RecruitCreatureEventSourcedState =
        RecruitCreatureEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged): RecruitCreatureEventSourcedState =
        RecruitCreatureEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: CreatureAddedToArmy): RecruitCreatureEventSourcedState =
        RecruitCreatureEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: CreatureRemovedFromArmy): RecruitCreatureEventSourcedState =
        RecruitCreatureEventSourcedState(evolve(state, event))

    companion object {
        @JvmStatic
        @EventCriteriaBuilder
        fun resolveCriteria(recruitmentId: RecruitCreature.RecruitmentId) =
            EventCriteria.either(
                EventCriteria
                    .havingTags(Tag.of(EventTags.DWELLING_ID, recruitmentId.dwellingId.raw))
                    .andBeingOneOfTypes(
                        DwellingBuilt::class.java.getName(),
                        AvailableCreaturesChanged::class.java.getName(),
                    ),
                EventCriteria
                    .havingTags(Tag.of(EventTags.ARMY_ID, recruitmentId.armyId.raw))
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
    ): CommandHandlerResult = resultOf {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metadata))
        events.toCommandResult()
    }
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.recruitcreature.enabled"])
@Configuration
internal class RecruitCreatureWriteSliceConfig {

    @Bean
    fun recruitCreatureSliceState(): EntityModule<*, *> =
        EventSourcedEntityModule.autodetected(
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

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.recruitcreature.enabled"])
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
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = RecruitCreature(
            dwellingId = DwellingId(dwellingId),
            creatureId = CreatureId(requestBody.creatureId),
            armyId = ArmyId(requestBody.armyId),
            quantity = Quantity(requestBody.quantity),
            expectedCost = Resources.of(requestBody.expectedCost)
        )

        val gameId = GameId(gameId)
        val playerId = PlayerId(playerId)
        val metadata = GameMetadata.with(gameId, playerId)
        val message = command.asCommandMessage(metadata)

        return commandGateway.send(message)
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}

