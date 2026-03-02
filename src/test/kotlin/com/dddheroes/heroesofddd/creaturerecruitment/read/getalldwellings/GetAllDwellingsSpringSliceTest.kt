package com.dddheroes.heroesofddd.creaturerecruitment.read.getalldwellings

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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.TimeUnit

@TestPropertySource(properties = ["slices.creaturerecruitment.read.getalldwellings.enabled=true"])
@HeroesAxonSpringBootTest
internal class GetAllDwellingsSpringSliceTest @Autowired constructor(
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
    fun `given no events, when get all dwellings, then empty result`() {
        fixture.`when`()
            .nothing()
            .then()
            .expect { cfg ->
                val result = queryAllDwellings(cfg)
                assertThat(result.items).isEmpty()
            }
    }

    @Test
    fun `given two dwellings built, when get all dwellings, then both returned`() {
        val dwellingId1 = DwellingId.random()
        val dwellingId2 = DwellingId.random()
        val creatureId = CreatureId("phoenix")
        val expectedCost = phoenixCostRaw

        fixture.given()
            .event(DwellingBuilt(dwellingId1, creatureId, phoenixCost), gameMetadata)
            .event(DwellingBuilt(dwellingId2, creatureId, phoenixCost), gameMetadata)
            .then()
            .await { r ->
                r.expect { cfg ->
                    val result = queryAllDwellings(cfg)
                    assertThat(result.items).containsExactlyInAnyOrder(
                        DwellingReadModel(gameId.raw, dwellingId1.raw, creatureId.raw, expectedCost, 0),
                        DwellingReadModel(gameId.raw, dwellingId2.raw, creatureId.raw, expectedCost, 0)
                    )
                }
            }
    }

    @Test
    fun `given dwelling built and creatures changed, when get all dwellings, then dwelling has updated creatures`() {
        val dwellingId = DwellingId.random()
        val creatureId = CreatureId("phoenix")

        fixture.given()
            .event(DwellingBuilt(dwellingId, creatureId, phoenixCost), gameMetadata)
            .event(
                AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 5, changedTo = Quantity(5)),
                gameMetadata
            )
            .then()
            .await { r ->
                r.expect { cfg ->
                    val result = queryAllDwellings(cfg)
                    val expectedCost = phoenixCostRaw
                    assertThat(result.items).containsExactlyInAnyOrder(
                        DwellingReadModel(gameId.raw, dwellingId.raw, creatureId.raw, expectedCost, 5)
                    )
                }
            }
    }

    private fun queryAllDwellings(cfg: Configuration): GetAllDwellings.Result =
        cfg.getComponent(QueryGateway::class.java)
            .query(GetAllDwellings(gameId), GetAllDwellings.Result::class.java)
            .orTimeout(1, TimeUnit.SECONDS)
            .join()
}
