package com.dddheroes.heroesofddd.resourcespool.write.depositresources

import com.dddheroes.heroesofddd.EventTags
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesDeposited
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesPoolEvent
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesWithdrawn
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.extension.spring.stereotype.EventSourced
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.messaging.commandhandling.annotation.Command
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.axonframework.modelling.annotation.InjectEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

@Command(namespace = "ResourcesPool", name = "DepositResources", version = "1.0.0")
data class DepositResources(
    @get:JvmName("getResourcesPoolId")
    val resourcesPoolId: ResourcesPoolId,
    val resources: Resources,
)

private data class State(val balance: Resources)

private val initialState = State(balance = Resources.empty())

private fun decide(command: DepositResources, @Suppress("UNUSED_PARAMETER") state: State): List<HeroesEvent> {
    return listOf(
        ResourcesDeposited(
            resourcesPoolId = command.resourcesPoolId,
            resources = command.resources
        )
    )
}

private fun evolve(state: State, event: ResourcesPoolEvent): State = when (event) {
    is ResourcesDeposited -> state.copy(balance = state.balance + event.resources)
    is ResourcesWithdrawn -> state.copy(balance = state.balance - event.resources)
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.resourcespool", name = ["write.depositresources.enabled"])
@EventSourced(tagKey = EventTags.RESOURCES_POOL_ID)
private class DepositResourcesEventSourcedState private constructor(val state: State) {

    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: ResourcesDeposited) = DepositResourcesEventSourcedState(evolve(state, event))

    @EventSourcingHandler
    fun evolve(event: ResourcesWithdrawn) = DepositResourcesEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.resourcespool", name = ["write.depositresources.enabled"])
@Component
private class DepositResourcesCommandHandler {

    @CommandHandler
    fun handle(
        command: DepositResources,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.RESOURCES_POOL_ID) eventSourced: DepositResourcesEventSourcedState,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events, metadata)
        events.toCommandResult()
    }
}
