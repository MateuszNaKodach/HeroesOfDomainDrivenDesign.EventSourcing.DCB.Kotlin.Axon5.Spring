package com.dddheroes.heroesofddd.armies.write.addcreaturetoarmy

import com.dddheroes.extensions.axon.test.AxonGatewaysMock
import com.dddheroes.extensions.axon.test.AxonGatewaysMockTest
import com.dddheroes.extensions.webmvc.test.RestAssuredMockMvcTest
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult.Failure
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult.Success
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import com.dddheroes.heroesofddd.shared.restapi.Headers
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@RestAssuredMockMvcTest
@AxonGatewaysMockTest
@TestPropertySource(properties = ["slices.armies.write.addcreaturetoarmy.enabled=true"])
internal class AddCreatureToArmyRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()
    private val playerId = PlayerId.random()
    private val armyId = ArmyId.random()

    @Test
    fun `command success - returns 204 No Content`() {
        gateways.assumeCommandReturns<AddCreatureToArmy>(Success)

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("armyId", armyId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "creatureId": "angel",
                  "quantity": 1
                }
                """
            )
        } When {
            async().post("/games/{gameId}/armies/{armyId}/creatures")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        gateways.assumeCommandReturns<AddCreatureToArmy>(Failure("Can have max 7 different creature stacks in the army"))

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("armyId", armyId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "creatureId": "phoenix",
                  "quantity": 3
                }
                """
            )
        } When {
            async().post("/games/{gameId}/armies/{armyId}/creatures")
        } Then {
            statusCode(HttpStatus.BAD_REQUEST.value())
            contentType(ContentType.JSON)
            body("message", equalTo("Can have max 7 different creature stacks in the army"))
        }
    }
}
