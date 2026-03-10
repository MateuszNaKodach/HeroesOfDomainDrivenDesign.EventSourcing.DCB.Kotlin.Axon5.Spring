package com.dddheroes.heroesofddd.armies.write.addcreaturetoarmy

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.armies.events.ArmyEvent
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.application.resultOf
import com.dddheroes.heroesofddd.shared.application.toCommandResult
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.restapi.Headers
import com.dddheroes.heroesofddd.shared.restapi.toResponseEntity
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

private const val MAX_CREATURE_STACKS = 7

@Command(namespace = "Armies", name = "AddCreatureToArmy", version = "1.0.0")
data class AddCreatureToArmy(
    @get:JvmName("getArmyId")
    val armyId: ArmyId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    @get:JvmName("getQuantity")
    val quantity: Quantity,
)

private data class State(val creatureStacks: Map<CreatureId, Quantity>) {
    companion object {
        val initialState = State(creatureStacks = emptyMap())
    }

    val distinctCreatureTypes: Int get() = creatureStacks.size

    fun hasCreature(creatureId: CreatureId): Boolean = creatureStacks.containsKey(creatureId)
}

private fun decide(command: AddCreatureToArmy, state: State): List<HeroesEvent> {
    if (!state.hasCreature(command.creatureId) && state.distinctCreatureTypes >= MAX_CREATURE_STACKS) {
        throw IllegalStateException("Can have max $MAX_CREATURE_STACKS different creature stacks in the army")
    }

    return listOf(
        CreatureAddedToArmy(
            armyId = command.armyId,
            creatureId = command.creatureId,
            quantity = command.quantity
        )
    )
}

private fun evolve(state: State, event: ArmyEvent): State = when (event) {
    is CreatureAddedToArmy -> {
        val currentQuantity = state.creatureStacks[event.creatureId] ?: Quantity.zero()
        state.copy(creatureStacks = state.creatureStacks + (event.creatureId to currentQuantity + event.quantity))
    }
    is CreatureRemovedFromArmy -> {
        val currentQuantity = state.creatureStacks[event.creatureId] ?: Quantity.zero()
        val newQuantity = currentQuantity - event.quantity
        if (newQuantity <= Quantity.zero()) {
            state.copy(creatureStacks = state.creatureStacks - event.creatureId)
        } else {
            state.copy(creatureStacks = state.creatureStacks + (event.creatureId to newQuantity))
        }
    }
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.armies", name = ["write.addcreaturetoarmy.enabled"])
@EventSourced(tagKey = EventTags.ARMY_ID)
private class AddCreatureToArmyEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(State.initialState)

    @EventSourcingHandler
    fun evolve(event: CreatureAddedToArmy) = AddCreatureToArmyEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: CreatureRemovedFromArmy) = AddCreatureToArmyEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.armies", name = ["write.addcreaturetoarmy.enabled"])
@Component
private class AddCreatureToArmyCommandHandler {

    @CommandHandler
    fun handle(
        command: AddCreatureToArmy,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.ARMY_ID) eventSourced: AddCreatureToArmyEventSourcedState,
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

@ConditionalOnProperty(prefix = "slices.armies", name = ["write.addcreaturetoarmy.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class AddCreatureToArmyRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(val creatureId: String, val quantity: Int)

    @PostMapping("/armies/{armyId}/creatures")
    fun postAddCreatureToArmy(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable armyId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = AddCreatureToArmy(
            ArmyId(armyId),
            CreatureId(requestBody.creatureId),
            Quantity(requestBody.quantity)
        )

        val gameId = GameId(gameId)
        val playerId = PlayerId(playerId)
        val metadata = GameMetadata.with(gameId, playerId)

        return commandGateway.send(command, metadata)
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
