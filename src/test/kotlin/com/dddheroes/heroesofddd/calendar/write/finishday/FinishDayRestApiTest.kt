package com.dddheroes.heroesofddd.calendar.write.finishday

import com.dddheroes.extensions.axon.test.AxonGatewaysMock
import com.dddheroes.extensions.axon.test.AxonGatewaysMockTest
import com.dddheroes.extensions.webmvc.test.RestAssuredMockMvcTest
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult.Failure
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult.Success
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
@TestPropertySource(properties = ["slices.calendar.write.finishday.enabled=true"])
internal class FinishDayRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()
    private val playerId = PlayerId.random()

    @Test
    fun `command success - returns 204 No Content`() {
        gateways.assumeCommandReturns<FinishDay>(Success)

        Given {
            pathParam("gameId", gameId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "month": 1,
                  "week": 1,
                  "day": 1
                }
                """
            )
        } When {
            async().post("/games/{gameId}/calendar/days/finish")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        gateways.assumeCommandReturns<FinishDay>(Failure("Can only finish current day"))

        Given {
            pathParam("gameId", gameId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "month": 1,
                  "week": 1,
                  "day": 1
                }
                """
            )
        } When {
            async().post("/games/{gameId}/calendar/days/finish")
        } Then {
            statusCode(HttpStatus.BAD_REQUEST.value())
            contentType(ContentType.JSON)
            body("message", equalTo("Can only finish current day"))
        }
    }
}
