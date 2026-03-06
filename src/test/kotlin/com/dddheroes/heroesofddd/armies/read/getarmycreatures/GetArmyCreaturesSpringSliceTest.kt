package com.dddheroes.heroesofddd.armies.read.getarmycreatures

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.common.configuration.Configuration
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.TimeUnit

@TestPropertySource(properties = ["slices.armies.read.getarmycreatures.enabled=true"])
@HeroesAxonSpringBootTest
internal class GetArmyCreaturesSpringSliceTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {

    private val gameId = GameId.random()
    private val armyId = ArmyId("hero-catherine-army")
    private val gameMetadata = AxonMetadata.with("gameId", gameId.raw)

    @Test
    fun `given no events, when get army creatures, then empty stacks`() {
        fixture.When { nothing() } Then {
            expect { cfg ->
                val result = queryArmyCreatures(cfg)
                assertThat(result.armyId).isEqualTo(armyId.raw)
                assertThat(result.stacks).isEmpty()
            }
        }
    }

    @Test
    fun `given creature added, when get army creatures, then one stack returned`() {
        fixture.Given {
            event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(5)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryArmyCreatures(cfg)
                assertThat(result.armyId).isEqualTo(armyId.raw)
                assertThat(result.stacks).containsExactlyInAnyOrder(
                    GetArmyCreatures.CreatureStack("angel", 5)
                )
            }
        }
    }

    @Test
    fun `given two different creatures added, when get army creatures, then two stacks returned`() {
        fixture.Given {
            event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(5)), gameMetadata)
            event(CreatureAddedToArmy(armyId, CreatureId("bowman"), Quantity(3)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryArmyCreatures(cfg)
                assertThat(result.stacks).containsExactlyInAnyOrder(
                    GetArmyCreatures.CreatureStack("angel", 5),
                    GetArmyCreatures.CreatureStack("bowman", 3)
                )
            }
        }
    }

    @Test
    fun `given same creature added twice, when get army creatures, then quantities aggregated`() {
        fixture.Given {
            event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(5)), gameMetadata)
            event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(3)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryArmyCreatures(cfg)
                assertThat(result.stacks).containsExactlyInAnyOrder(
                    GetArmyCreatures.CreatureStack("angel", 8)
                )
            }
        }
    }

    @Test
    fun `given creature added then partially removed, when get army creatures, then reduced quantity`() {
        fixture.Given {
            event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(5)), gameMetadata)
            event(CreatureRemovedFromArmy(armyId, CreatureId("angel"), Quantity(2)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryArmyCreatures(cfg)
                assertThat(result.stacks).containsExactlyInAnyOrder(
                    GetArmyCreatures.CreatureStack("angel", 3)
                )
            }
        }
    }

    @Test
    fun `given creature added then fully removed, when get army creatures, then stack removed`() {
        fixture.Given {
            event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(5)), gameMetadata)
            event(CreatureRemovedFromArmy(armyId, CreatureId("angel"), Quantity(5)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryArmyCreatures(cfg)
                assertThat(result.stacks).isEmpty()
            }
        }
    }

    @Test
    fun `given creatures from different armies, when get army creatures, then only requested army returned`() {
        val otherArmyId = ArmyId("hero-roland-army")

        fixture.Given {
            event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(5)), gameMetadata)
            event(CreatureAddedToArmy(otherArmyId, CreatureId("black-dragon"), Quantity(2)), gameMetadata)
        } Then {
            awaitAndExpect { cfg ->
                val result = queryArmyCreatures(cfg)
                assertThat(result.stacks).containsExactlyInAnyOrder(
                    GetArmyCreatures.CreatureStack("angel", 5)
                )
            }
        }
    }

    private fun queryArmyCreatures(cfg: Configuration): GetArmyCreatures.Result =
        cfg.getComponent(QueryGateway::class.java)
            .query(GetArmyCreatures(gameId, armyId), GetArmyCreatures.Result::class.java)
            .orTimeout(1, TimeUnit.SECONDS)
            .join()
}
