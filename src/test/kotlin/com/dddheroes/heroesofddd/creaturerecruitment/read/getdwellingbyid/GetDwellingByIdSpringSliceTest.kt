package com.dddheroes.heroesofddd.creaturerecruitment.read.getdwellingbyid

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.common.configuration.Configuration
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.Given
import org.axonframework.test.fixture.Then
import org.axonframework.test.fixture.awaitAndExpect
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.TimeUnit

@TestPropertySource(properties = ["slices.creaturerecruitment.read.getdwellingbyid.enabled=true"])
@HeroesAxonSpringBootTest
internal class GetDwellingByIdSpringSliceTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {

    private val gameId = GameId.random()
    private val gameMetadata = AxonMetadata.with("gameId", gameId.raw)
    private val phoenixCost = Resources.of(ResourceType.GOLD to 2000, ResourceType.MERCURY to 1)
    private val phoenixCostRaw = mapOf(
        "GOLD" to 2000, "WOOD" to 0, "ORE" to 0,
        "MERCURY" to 1, "SULFUR" to 0, "CRYSTAL" to 0, "GEMS" to 0
    )

    @Test
    fun `given dwelling built, when get dwelling by id, then dwelling returned`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("phoenix")

        fixture.Given {
            event(DwellingBuilt(dwellingId, creatureId, phoenixCost), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryDwellingById(cfg, dwellingId)
                assertThat(result).isEqualTo(
                    GetDwellingById.Result(dwellingId.raw, creatureId.raw, phoenixCostRaw, 0)
                )
            }
        }
    }

    @Test
    fun `given dwelling built and creatures changed, when get dwelling by id, then updated creatures returned`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("phoenix")

        fixture.Given {
            event(DwellingBuilt(dwellingId, creatureId, phoenixCost), gameMetadata)
            event(
                AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 5, changedTo = Quantity(5)),
                gameMetadata
            )
        } Then {
            awaitAndExpect { cfg ->
                val result = queryDwellingById(cfg, dwellingId)
                assertThat(result).isEqualTo(
                    GetDwellingById.Result(dwellingId.raw, creatureId.raw, phoenixCostRaw, 5)
                )
            }
        }
    }

    @Test
    fun `given two dwellings built, when get dwelling by first id, then only first dwelling returned`() {
        val dwellingId1 = DwellingId.random()
        val dwellingId2 = DwellingId.random()
        val creatureId = CreatureId("phoenix")

        fixture.Given {
            event(DwellingBuilt(dwellingId1, creatureId, phoenixCost), gameMetadata)
            event(DwellingBuilt(dwellingId2, creatureId, phoenixCost), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryDwellingById(cfg, dwellingId1)
                assertThat(result).isEqualTo(
                    GetDwellingById.Result(dwellingId1.raw, creatureId.raw, phoenixCostRaw, 0)
                )
            }
        }
    }

    private fun queryDwellingById(cfg: Configuration, dwellingId: DwellingId): GetDwellingById.Result =
        cfg.getComponent(QueryGateway::class.java)
            .query(GetDwellingById(gameId, dwellingId), GetDwellingById.Result::class.java)
            .orTimeout(1, TimeUnit.SECONDS)
            .join()
}
