package com.dddheroes.heroesofddd.armies.write.removecreaturefromarmy

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.armies.events.ArmyEvent
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
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

@Command(namespace = "Armies", name = "RemoveCreatureFromArmy", version = "1.0.0")
data class RemoveCreatureFromArmy(
    @get:JvmName("getArmyId")
    val armyId: ArmyId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    @get:JvmName("getQuantity")
    val quantity: Quantity,
)

private data class State(val creatureStacks: Map<CreatureId, Quantity>) {
    fun quantityOf(creatureId: CreatureId): Quantity = creatureStacks[creatureId] ?: Quantity.zero()
}

private val initialState = State(creatureStacks = emptyMap())

private fun decide(command: RemoveCreatureFromArmy, state: State): List<HeroesEvent> {
    if (state.quantityOf(command.creatureId) < command.quantity) {
        throw IllegalStateException("Can remove only present creatures")
    }

    return listOf(
        CreatureRemovedFromArmy(
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

@ConditionalOnProperty(prefix = "slices.armies", name = ["write.removecreaturefromarmy.enabled"])
@EventSourced(tagKey = EventTags.ARMY_ID)
private class RemoveCreatureFromArmyEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: CreatureAddedToArmy) = RemoveCreatureFromArmyEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: CreatureRemovedFromArmy) = RemoveCreatureFromArmyEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.armies", name = ["write.removecreaturefromarmy.enabled"])
@Component
private class RemoveCreatureFromArmyCommandHandler {

    @CommandHandler
    fun handle(
        command: RemoveCreatureFromArmy,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.ARMY_ID) eventSourced: RemoveCreatureFromArmyEventSourcedState,
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

@ConditionalOnProperty(prefix = "slices.armies", name = ["write.removecreaturefromarmy.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class RemoveCreatureFromArmyRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(val quantity: Int)

    @DeleteMapping("/armies/{armyId}/creatures/{creatureId}")
    fun deleteRemoveCreatureFromArmy(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable armyId: String,
        @PathVariable creatureId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = RemoveCreatureFromArmy(
            ArmyId(armyId),
            CreatureId(creatureId),
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
