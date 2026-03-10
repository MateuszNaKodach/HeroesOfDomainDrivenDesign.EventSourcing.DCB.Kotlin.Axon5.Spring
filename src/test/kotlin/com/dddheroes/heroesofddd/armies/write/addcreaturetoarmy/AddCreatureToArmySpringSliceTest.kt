package com.dddheroes.heroesofddd.armies.write.addcreaturetoarmy

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult.Failure
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult.Success
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.armies.write.addcreaturetoarmy.enabled=true"])
@HeroesAxonSpringBootTest
internal class AddCreatureToArmySpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    private val armyId = ArmyId.random()

    @Test
    fun `given empty army, when add creature, then success`() {
        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(
                    AddCreatureToArmy(armyId, CreatureId("Angel"), Quantity(1)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(1)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given some creatures in the army, when add creature, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Centaur"), Quantity(5)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Bowman"), Quantity(3)), gameMetadata)
            } When {
                command(
                    AddCreatureToArmy(armyId, CreatureId("Angel"), Quantity(1)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(1)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given army with max creature stacks, when add creature, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Centaur"), Quantity(5)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("ArchAngel"), Quantity(3)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("BlackDragon"), Quantity(9)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("RedDragon"), Quantity(15)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Bowman"), Quantity(12)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Behemoth"), Quantity(11)), gameMetadata)
            } When {
                command(
                    AddCreatureToArmy(armyId, CreatureId("Phoenix"), Quantity(3)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Failure("Can have max 7 different creature stacks in the army"))
                noEvents()
            }
        }
    }

    @Test
    fun `given army with max creature stacks, when add present creature, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Centaur"), Quantity(5)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("ArchAngel"), Quantity(3)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("BlackDragon"), Quantity(9)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("RedDragon"), Quantity(15)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Bowman"), Quantity(12)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Behemoth"), Quantity(11)), gameMetadata)
            } When {
                command(
                    AddCreatureToArmy(armyId, CreatureId("ArchAngel"), Quantity(3)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(CreatureAddedToArmy(armyId, CreatureId("ArchAngel"), Quantity(3)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given army with max creature stacks after removal, when add new creature, then success`() {
        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("Centaur"), Quantity(5)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Angel"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("ArchAngel"), Quantity(3)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("BlackDragon"), Quantity(9)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("RedDragon"), Quantity(15)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Bowman"), Quantity(12)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("Behemoth"), Quantity(11)), gameMetadata)
                event(CreatureRemovedFromArmy(armyId, CreatureId("Behemoth"), Quantity(11)), gameMetadata)
            } When {
                command(
                    AddCreatureToArmy(armyId, CreatureId("Phoenix"), Quantity(3)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(CreatureAddedToArmy(armyId, CreatureId("Phoenix"), Quantity(3)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }
}
