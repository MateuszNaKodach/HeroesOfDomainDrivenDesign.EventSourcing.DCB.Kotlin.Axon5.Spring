package com.dddheroes.heroesofddd.armies.write.removecreaturefromarmy

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.sdk.application.CommandHandlerResult.Failure
import com.dddheroes.sdk.application.CommandHandlerResult.Success
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
    fun `given creature in army, when remove creature, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(5)), gameMetadata)
            } When {
                command(
                    RemoveCreatureFromArmy(armyId, CreatureId("Angel"), Quantity(3)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(CreatureRemovedFromArmy(armyId, CreatureId("Angel"), Quantity(3)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given creature in army, when remove all creatures, then success and stack removed`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(5)), gameMetadata)
            } When {
                command(
                    RemoveCreatureFromArmy(armyId, CreatureId("Angel"), Quantity(5)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(CreatureRemovedFromArmy(armyId, CreatureId("Angel"), Quantity(5)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given creature not in army, when remove creature, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(
                    RemoveCreatureFromArmy(armyId, CreatureId("Angel"), Quantity(1)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Failure("Can remove only present creatures"))
                noEvents()
            }
        }
    }

    @Test
    fun `given not enough creatures in army, when remove more than present, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(3)), gameMetadata)
            } When {
                command(
                    RemoveCreatureFromArmy(armyId, CreatureId("Angel"), Quantity(5)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Failure("Can remove only present creatures"))
                noEvents()
            }
        }
    }

    @Test
    fun `given creature added twice, when remove partial, then success with accumulated quantity tracked`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(3)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(2)), gameMetadata)
            } When {
                command(
                    RemoveCreatureFromArmy(armyId, CreatureId("Angel"), Quantity(4)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(CreatureRemovedFromArmy(armyId, CreatureId("Angel"), Quantity(4)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given creature stack removed, when remove same creature again, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(3)), gameMetadata)
                event(CreatureRemovedFromArmy(armyId, CreatureId("Angel"), Quantity(3)), gameMetadata)
            } When {
                command(
                    RemoveCreatureFromArmy(armyId, CreatureId("Angel"), Quantity(1)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Failure("Can remove only present creatures"))
                noEvents()
            }
        }
    }
}
