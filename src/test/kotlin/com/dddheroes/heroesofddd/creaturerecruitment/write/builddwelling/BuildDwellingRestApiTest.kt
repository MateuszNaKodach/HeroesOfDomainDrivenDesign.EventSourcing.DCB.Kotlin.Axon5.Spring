package com.dddheroes.heroesofddd.creaturerecruitment.write.builddwelling

import com.dddheroes.heroesofddd.RestApiSpringBootTest
import com.dddheroes.heroesofddd.shared.restapi.Headers
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.TestPropertySource
import java.util.*

@WebMvcTest
@TestPropertySource(properties = ["slices.creaturerecruitment.write.builddwelling.enabled=true"])
internal class BuildDwellingRestApiTest : RestApiSpringBootTest() {

    private val gameId = UUID.randomUUID().toString()
    private val playerId = UUID.randomUUID().toString()
    private val dwellingId = UUID.randomUUID().toString()

    @Test
    fun `when build dwelling command succeeds then return 204 No Content`() {
        assumeCommandSuccess()

        Given {
            contentType(ContentType.JSON)
            header(Headers.PLAYER_ID, playerId)
            body("""{"creatureId": "angel", "costPerTroop": {"gold": 3000}}""")
        } When {
            async().put("/games/$gameId/dwellings/$dwellingId")
        } Then {
            statusCode(204)
        }
    }

    @Test
    fun `when build dwelling command fails then return 400 Bad Request`() {
        assumeCommandFailure("Dwelling already built")

        Given {
            contentType(ContentType.JSON)
            header(Headers.PLAYER_ID, playerId)
            body("""{"creatureId": "angel", "costPerTroop": {"gold": 3000}}""")
        } When {
            async().put("/games/$gameId/dwellings/$dwellingId")
        } Then {
            statusCode(400)
            body("message", equalTo("Dwelling already built"))
        }
    }
}
