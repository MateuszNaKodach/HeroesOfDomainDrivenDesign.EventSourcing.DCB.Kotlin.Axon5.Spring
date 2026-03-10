package com.dddheroes.heroesofddd.armies.write.removecreaturefromarmy

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

@Command(namespace = "Armies", name = "RemoveCreatureFromArmy", version = "1.0.0")
data class RemoveCreatureFromArmy(
    @get:JvmName("getArmyId")
    val armyId: ArmyId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    val quantity: Quantity
)

private data class State(val creatureQuantities: Map<CreatureId, Quantity>) {
    companion object {
        val initialState = State(creatureQuantities = emptyMap())
    }
}

private fun decide(command: RemoveCreatureFromArmy, state: State): List<HeroesEvent> {
    val currentQuantity = state.creatureQuantities[command.creatureId]
        ?: return emptyList() // creature not present — idempotent no-op

    if (currentQuantity < command.quantity) {
        throw IllegalStateException("Cannot remove more creatures than present in army")
    }

    if (currentQuantity == Quantity.zero()) {
        return emptyList() // already fully removed — idempotent
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
        val current = state.creatureQuantities[event.creatureId] ?: Quantity.zero()
        state.copy(creatureQuantities = state.creatureQuantities + (event.creatureId to current + event.quantity))
    }
    is CreatureRemovedFromArmy -> {
        val current = state.creatureQuantities[event.creatureId] ?: Quantity.zero()
        val newQuantity = current - event.quantity
        if (newQuantity == Quantity.zero()) {
            state.copy(creatureQuantities = state.creatureQuantities - event.creatureId)
        } else {
            state.copy(creatureQuantities = state.creatureQuantities + (event.creatureId to newQuantity))
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
    constructor() : this(State.initialState)

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
    fun removeCreatureFromArmy(
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

        val metadata = GameMetadata.with(GameId(gameId), PlayerId(playerId))

        return commandGateway.send(command, metadata)
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
