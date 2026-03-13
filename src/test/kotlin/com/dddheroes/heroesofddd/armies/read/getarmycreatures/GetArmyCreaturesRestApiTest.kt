package com.dddheroes.heroesofddd.armies.read.getarmycreatures

import com.dddheroes.extensions.webmvc.test.RestAssuredMockMvcTest
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.axonframework.extensions.spring.test.AxonGatewaysMock
import org.axonframework.extensions.spring.test.AxonGatewaysMockTest
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@RestAssuredMockMvcTest
@AxonGatewaysMockTest
@TestPropertySource(properties = ["slices.armies.read.getarmycreatures.enabled=true"])
internal class GetArmyCreaturesRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()
    private val armyId = ArmyId("hero-catherine-army")

    @Test
    fun `returns army creatures`() {
        val query = GetArmyCreatures(gameId, armyId)
        val result = GetArmyCreatures.Result(
            armyId.raw,
            listOf(
                GetArmyCreatures.CreatureStack("angel", 5),
                GetArmyCreatures.CreatureStack("bowman", 3)
            )
        )
        gateways.assumeQueryReturns(query, result)

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("armyId", armyId.raw)
        } When {
            async().get("/games/{gameId}/armies/{armyId}/creatures")
        } Then {
            statusCode(HttpStatus.OK.value())
            contentType(ContentType.JSON)
            body("armyId", equalTo(armyId.raw))
            body("stacks", hasSize<Int>(2))
            body("stacks[0].creatureId", equalTo("angel"))
            body("stacks[0].quantity", equalTo(5))
            body("stacks[1].creatureId", equalTo("bowman"))
            body("stacks[1].quantity", equalTo(3))
        }
    }

    @Test
    fun `returns empty stacks when no creatures`() {
        val query = GetArmyCreatures(gameId, armyId)
        val result = GetArmyCreatures.Result(armyId.raw, emptyList())
        gateways.assumeQueryReturns(query, result)

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("armyId", armyId.raw)
        } When {
            async().get("/games/{gameId}/armies/{armyId}/creatures")
        } Then {
            statusCode(HttpStatus.OK.value())
            contentType(ContentType.JSON)
            body("armyId", equalTo(armyId.raw))
            body("stacks", hasSize<Int>(0))
        }
    }
}
