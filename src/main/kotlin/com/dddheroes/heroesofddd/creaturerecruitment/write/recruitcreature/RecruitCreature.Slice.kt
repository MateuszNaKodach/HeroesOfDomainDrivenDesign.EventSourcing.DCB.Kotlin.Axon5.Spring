package com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruited
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingEvent
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.*
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.heroesofddd.shared.restapi.Headers
import com.dddheroes.sdk.application.CommandHandlerResult
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.restapi.toResponseEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.messaging.commandhandling.annotation.Command
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

@Command(namespace = "CreatureRecruitment", name = "RecruitCreature", version = "1.0.0")
data class RecruitCreature(
    @get:JvmName("getDwellingId")
    val dwellingId: DwellingId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    val armyId: ArmyId,
    val quantity: Quantity,
    val expectedCost: Resources,
)

private data class State(
    val creatureId: CreatureId,
    val availableCreatures: Quantity,
    val costPerTroop: Resources,
)

private val initialState = State(
    creatureId = CreatureId("undefined"),
    availableCreatures = Quantity.zero(),
    costPerTroop = Resources.empty(),
)

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

    return listOf(
        CreatureRecruited(
            dwellingId = command.dwellingId,
            creatureId = command.creatureId,
            toArmy = command.armyId,
            quantity = command.quantity,
            totalCost = recruitCost
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

    is CreatureRecruited ->
        state.copy(availableCreatures = state.availableCreatures - event.quantity)
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.recruitcreature.enabled"])
@EventSourced(tagKey = EventTags.DWELLING_ID) // ConsistencyBoundary
private class RecruitCreatureEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = RecruitCreatureEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged) = RecruitCreatureEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: CreatureRecruited) = RecruitCreatureEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.recruitcreature.enabled"])
@Component
private class RecruitCreatureCommandHandler {

    @CommandHandler
    fun handle(
        command: RecruitCreature,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: RecruitCreatureEventSourcedState,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events, metadata)
        events.toCommandResult()
    }
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

        return commandGateway.send(command, metadata)
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
