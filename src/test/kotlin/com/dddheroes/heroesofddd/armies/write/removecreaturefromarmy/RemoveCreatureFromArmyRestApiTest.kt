package com.dddheroes.heroesofddd.armies.write.removecreaturefromarmy

import com.dddheroes.extensions.webmvc.test.RestAssuredMockMvcTest
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.restapi.Headers
import com.dddheroes.sdk.application.CommandHandlerResult.Failure
import com.dddheroes.sdk.application.CommandHandlerResult.Success
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.axonframework.extensions.spring.test.AxonGatewaysMock
import org.axonframework.extensions.spring.test.AxonGatewaysMockTest
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@RestAssuredMockMvcTest
@AxonGatewaysMockTest
@TestPropertySource(properties = ["slices.armies.write.removecreaturefromarmy.enabled=true"])
internal class RemoveCreatureFromArmyRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()
    private val playerId = PlayerId.random()
    private val armyId = ArmyId.random()
    private val creatureId = CreatureId("Angel")

    @Test
    fun `command success - returns 204 No Content`() {
        gateways.assumeCommandReturns<RemoveCreatureFromArmy>(Success)

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("armyId", armyId.raw)
            pathParam("creatureId", creatureId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "quantity": 3
                }
                """
            )
        } When {
            async().delete("/games/{gameId}/armies/{armyId}/creatures/{creatureId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        gateways.assumeCommandReturns<RemoveCreatureFromArmy>(Failure("Can remove only present creatures"))

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("armyId", armyId.raw)
            pathParam("creatureId", creatureId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "quantity": 10
                }
                """
            )
        } When {
            async().delete("/games/{gameId}/armies/{armyId}/creatures/{creatureId}")
        } Then {
            statusCode(HttpStatus.BAD_REQUEST.value())
            contentType(ContentType.JSON)
            body("message", equalTo("Can remove only present creatures"))
        }
    }
}
