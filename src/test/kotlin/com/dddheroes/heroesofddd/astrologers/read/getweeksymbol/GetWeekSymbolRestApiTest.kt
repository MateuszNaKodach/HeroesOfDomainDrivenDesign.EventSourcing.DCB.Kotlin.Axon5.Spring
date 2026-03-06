package com.dddheroes.heroesofddd.astrologers.read.getweeksymbol

import com.dddheroes.extensions.axon.test.AxonGatewaysMock
import com.dddheroes.extensions.axon.test.AxonGatewaysMockTest
import com.dddheroes.extensions.webmvc.test.RestAssuredMockMvcTest
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
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
@TestPropertySource(properties = ["slices.astrologers.read.getweeksymbol.enabled=true"])
internal class GetWeekSymbolRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()

    @Test
    fun `returns week symbol`() {
        val query = GetWeekSymbol(gameId, month = 1, week = 2)
        val result = WeekSymbolReadModel(gameId.raw, month = 1, week = 2, weekOf = "angel", growth = 5)
        gateways.assumeQueryReturns(query, result)

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("month", 1)
            pathParam("week", 2)
        } When {
            async().get("/games/{gameId}/astrologers/week-symbol/{month}/{week}")
        } Then {
            statusCode(HttpStatus.OK.value())
            contentType(ContentType.JSON)
            body("month", equalTo(1))
            body("week", equalTo(2))
            body("weekOf", equalTo("angel"))
            body("growth", equalTo(5))
        }
    }

    @Test
    fun `returns 404 when no symbol for given week`() {
        val query = GetWeekSymbol(gameId, month = 1, week = 3)
        gateways.assumeQueryReturns<WeekSymbolReadModel?, GetWeekSymbol>(query, null)

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("month", 1)
            pathParam("week", 3)
        } When {
            async().get("/games/{gameId}/astrologers/week-symbol/{month}/{week}")
        } Then {
            statusCode(HttpStatus.NOT_FOUND.value())
        }
    }
}
