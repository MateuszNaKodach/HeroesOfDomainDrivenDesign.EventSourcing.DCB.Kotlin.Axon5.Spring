package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.extensions.webmvc.test.AxonMockMvc
import com.dddheroes.extensions.webmvc.test.AxonWebMvcTest
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
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

@AxonWebMvcTest
@TestPropertySource(properties = ["slices.creaturerecruitment.write.builddwelling.enabled=true"])
internal class BuildDwellingRestApiTest {

    @Autowired
    lateinit var axonMockMvc: AxonMockMvc

    private val gameId = GameId.random()
    private val playerId = PlayerId.random()
    private val dwellingId = DwellingId.random()
    private val creatureId = "angel"
    private val costGold = 3000

    @Test
    fun `command success - returns 204 No Content`() {
        axonMockMvc.assumeCommandSuccess<BuildDwelling>()

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("dwellingId", dwellingId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "creatureId": "$creatureId",
                  "costPerTroop": {"gold": $costGold}
                }
                """
            )
        } When {
            async().put("/games/{gameId}/dwellings/{dwellingId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        axonMockMvc.assumeCommandFailure<BuildDwelling>("Dwelling already built")

        Given {
            pathParam("gameId", gameId.raw)
            pathParam("dwellingId", dwellingId.raw)
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "creatureId": "$creatureId",
                  "costPerTroop": {"gold": $costGold}
                }
                """
            )
        } When {
            async().put("/games/{gameId}/dwellings/{dwellingId}")
        } Then {
            statusCode(HttpStatus.BAD_REQUEST.value())
            contentType(ContentType.JSON)
            body("message", equalTo("Dwelling already built"))
        }
    }
}
