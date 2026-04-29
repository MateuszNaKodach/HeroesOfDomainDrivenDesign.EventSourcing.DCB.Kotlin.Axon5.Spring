package com.dddheroes.heroesofddd.resourcespool.write.depositresources

import com.dddheroes.heroesofddd.resourcespool.events.ResourcesDeposited
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesPoolEvent
import com.dddheroes.heroesofddd.shared.domain.HeroesEvent
import com.dddheroes.heroesofddd.shared.domain.identifiers.ResourcesPoolId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.messaging.commandhandling.annotation.Command
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.eventhandling.gateway.EventAppender
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

typealias State = Nothing?

private fun decide(command: DepositResources, @Suppress("UNUSED_PARAMETER") state: State): List<HeroesEvent> {
    return listOf(
        ResourcesDeposited(
            resourcesPoolId = command.resourcesPoolId,
            resources = command.resources
        )
    )
}

private fun evolve(state: State, event: ResourcesPoolEvent): State {
    throw IllegalStateException("Resources can be deposited independent of state")
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.resourcespool", name = ["write.depositresources.enabled"])
@Component
private class DepositResourcesCommandHandler {

    @CommandHandler
    fun handle(
        command: DepositResources,
        metadata: AxonMetadata,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = decide(command, null)
        eventAppender.append(events, metadata)
        events.toCommandResult()
    }
}
