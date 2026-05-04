package com.dddheroes.heroesofddd.creaturerecruitment.write.requestcreaturerecruitment

import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruitmentRequested
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.*
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.heroesofddd.shared.restapi.Headers
import com.dddheroes.sdk.application.CommandHandlerResult
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.restapi.toResponseEntity
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.messaging.commandhandling.annotation.Command
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.eventhandling.gateway.EventAppender
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

@Command(namespace = "CreatureRecruitment", name = "RequestCreatureRecruitment", version = "1.0.0")
data class RequestCreatureRecruitment(
    @get:JvmName("getRecruitmentId")
    val recruitmentId: RecruitmentId,
    @get:JvmName("getDwellingId")
    val dwellingId: DwellingId,
    val resourcesPoolId: ResourcesPoolId,
    @get:JvmName("getCreatureId")
    val creatureId: CreatureId,
    val armyId: ArmyId,
    val quantity: Quantity,
    val expectedCost: Resources,
)

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.requestcreaturerecruitment.enabled"])
@Component
private class RequestCreatureRecruitmentCommandHandler {

    @CommandHandler
    fun handle(
        command: RequestCreatureRecruitment,
        metadata: AxonMetadata,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = listOf(
            CreatureRecruitmentRequested(
                recruitmentId = command.recruitmentId,
                dwellingId = command.dwellingId,
                resourcesPoolId = command.resourcesPoolId,
                creatureId = command.creatureId,
                armyId = command.armyId,
                quantity = command.quantity,
                expectedCost = command.expectedCost
            )
        )
        eventAppender.append(events, metadata)
        events.toCommandResult()
    }
}

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.requestcreaturerecruitment.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class RequestCreatureRecruitmentRestApi(private val commandGateway: CommandGateway) {

    data class Body(
        val creatureId: String,
        val armyId: String,
        val quantity: Int,
        val expectedCost: Map<String, Int>
    )

    @PutMapping("/dwellings/{dwellingId}/creature-recruitment-requests/{recruitmentId}")
    fun putCreatureRecruitmentRequest(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
        @PathVariable recruitmentId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val gameId = GameId(gameId)
        val playerId = PlayerId(playerId)
        val command = RequestCreatureRecruitment(
            recruitmentId = RecruitmentId(recruitmentId),
            dwellingId = DwellingId(dwellingId),
            resourcesPoolId = ResourcesPoolId(playerId.raw),
            creatureId = CreatureId(requestBody.creatureId),
            armyId = ArmyId(requestBody.armyId),
            quantity = Quantity(requestBody.quantity),
            expectedCost = Resources.of(requestBody.expectedCost)
        )
        return commandGateway.send(command, GameMetadata.with(gameId, playerId))
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
