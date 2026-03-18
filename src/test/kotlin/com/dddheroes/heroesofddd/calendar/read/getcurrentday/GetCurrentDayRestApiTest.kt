package com.dddheroes.heroesofddd.calendar.read.getcurrentday

import com.dddheroes.extensions.webmvc.test.RestAssuredMockMvcTest
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
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
@TestPropertySource(properties = ["slices.calendar.read.getcurrentday.enabled=true"])
internal class GetCurrentDayRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()

    @Test
    fun `returns current day`() {
        val query = GetCurrentDay(gameId)
        val result = CurrentDayReadModel(gameId.raw, month = 1, week = 2, day = 3, finished = false)
        gateways.assumeQueryReturns(query, result)

        Given {
            pathParam("gameId", gameId.raw)
        } When {
            async().get("/games/{gameId}/current-day")
        } Then {
            statusCode(HttpStatus.OK.value())
            contentType(ContentType.JSON)
            body("month", equalTo(1))
            body("week", equalTo(2))
            body("day", equalTo(3))
            body("finished", equalTo(false))
        }
    }

    @Test
    fun `returns 404 when no day started`() {
        val query = GetCurrentDay(gameId)
        gateways.assumeQueryReturnsNull(query)

        Given {
            pathParam("gameId", gameId.raw)
        } When {
            async().get("/games/{gameId}/current-day")
        } Then {
            statusCode(HttpStatus.NOT_FOUND.value())
        }
    }
}
