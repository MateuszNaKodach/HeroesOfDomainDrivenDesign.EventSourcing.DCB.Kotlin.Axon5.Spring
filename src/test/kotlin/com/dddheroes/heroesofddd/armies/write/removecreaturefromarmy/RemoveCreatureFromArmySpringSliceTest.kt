package com.dddheroes.heroesofddd.armies.write.removecreaturefromarmy

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.armies.write.removecreaturefromarmy.enabled=true"])
@HeroesAxonSpringBootTest
internal class RemoveCreatureFromArmySpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    private val armyId = ArmyId.random()

    @Test
    fun `army has creature, remove exact quantity`() {
        val centaur = CreatureId("Centaur")
        val bowman = CreatureId("Bowman")

        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, centaur, Quantity(5)), gameMetadata)
                event(CreatureAddedToArmy(armyId, bowman, Quantity(99)), gameMetadata)
            } When {
                command(RemoveCreatureFromArmy(armyId, centaur, Quantity(5)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(CreatureRemovedFromArmy(armyId, centaur, Quantity(5)))
            }
        }
    }

    @Test
    fun `army has creature, remove partial quantity`() {
        val centaur = CreatureId("Centaur")

        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, centaur, Quantity(5)), gameMetadata)
            } When {
                command(RemoveCreatureFromArmy(armyId, centaur, Quantity(3)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(CreatureRemovedFromArmy(armyId, centaur, Quantity(3)))
            }
        }
    }

    @Test
    fun `creature not present in army - idempotent no-op`() {
        val centaur = CreatureId("Centaur")
        val angel = CreatureId("Angel")

        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, centaur, Quantity(5)), gameMetadata)
            } When {
                command(RemoveCreatureFromArmy(armyId, angel, Quantity(1)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                noEvents()
            }
        }
    }

    @Test
    fun `remove more than available - failure`() {
        val centaur = CreatureId("Centaur")

        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, centaur, Quantity(5)), gameMetadata)
            } When {
                command(RemoveCreatureFromArmy(armyId, centaur, Quantity(6)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Cannot remove more creatures than present in army"))
            }
        }
    }

    @Test
    fun `creature already fully removed - idempotent replay`() {
        val centaur = CreatureId("Centaur")

        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, centaur, Quantity(5)), gameMetadata)
                event(CreatureRemovedFromArmy(armyId, centaur, Quantity(5)), gameMetadata)
            } When {
                command(RemoveCreatureFromArmy(armyId, centaur, Quantity(5)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                noEvents()
            }
        }
    }
}
