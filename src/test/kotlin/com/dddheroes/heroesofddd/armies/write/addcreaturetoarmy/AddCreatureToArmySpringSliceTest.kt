package com.dddheroes.heroesofddd.armies.write.addcreaturetoarmy

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.events.CreatureRemovedFromArmy
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Nested
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

    @Test
    fun `given empty army, when add creature, then creature added`() {
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")

        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(
                    AddCreatureToArmy(armyId = armyId, creatureId = creatureId, quantity = Quantity(1)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(CreatureAddedToArmy(armyId = armyId, creatureId = creatureId, quantity = Quantity(1)))
            }
        }
    }

    @Test
    fun `given some creatures in the army, when add creature, then creature added`() {
        val armyId = ArmyId.random()

        sliceUnderTest.Scenario {
            Given {
                event(CreatureAddedToArmy(armyId, CreatureId("centaur"), Quantity(5)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("bowman"), Quantity(3)), gameMetadata)
            } When {
                command(
                    AddCreatureToArmy(armyId = armyId, creatureId = CreatureId("angel"), quantity = Quantity(1)),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(CreatureAddedToArmy(armyId = armyId, creatureId = CreatureId("angel"), quantity = Quantity(1)))
            }
        }
    }

    @Nested
    inner class MaxCreatureStacksTests {

        @Test
        fun `given army with max creature stacks, when add new creature type, then failure`() {
            val armyId = ArmyId.random()

            sliceUnderTest.Scenario {
                Given {
                    event(CreatureAddedToArmy(armyId, CreatureId("centaur"), Quantity(5)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(1)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("archangel"), Quantity(3)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("black-dragon"), Quantity(9)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("red-dragon"), Quantity(15)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("bowman"), Quantity(12)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("behemoth"), Quantity(11)), gameMetadata)
                } When {
                    command(
                        AddCreatureToArmy(armyId = armyId, creatureId = CreatureId("phoenix"), quantity = Quantity(3)),
                        gameMetadata
                    )
                } Then {
                    resultMessagePayload(CommandHandlerResult.Failure("Can have max 7 different creature stacks in the army"))
                }
            }
        }

        @Test
        fun `given army with max creature stacks, when add present creature, then creature added`() {
            val armyId = ArmyId.random()

            sliceUnderTest.Scenario {
                Given {
                    event(CreatureAddedToArmy(armyId, CreatureId("centaur"), Quantity(5)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(1)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("archangel"), Quantity(3)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("black-dragon"), Quantity(9)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("red-dragon"), Quantity(15)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("bowman"), Quantity(12)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("behemoth"), Quantity(11)), gameMetadata)
                } When {
                    command(
                        AddCreatureToArmy(armyId = armyId, creatureId = CreatureId("archangel"), quantity = Quantity(3)),
                        gameMetadata
                    )
                } Then {
                    resultMessagePayload(CommandHandlerResult.Success)
                    events(CreatureAddedToArmy(armyId = armyId, creatureId = CreatureId("archangel"), quantity = Quantity(3)))
                }
            }
        }

        @Test
        fun `given army with max creature stacks after removal, when add new creature, then creature added`() {
            val armyId = ArmyId.random()

            sliceUnderTest.Scenario {
                Given {
                    event(CreatureAddedToArmy(armyId, CreatureId("centaur"), Quantity(5)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("angel"), Quantity(1)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("archangel"), Quantity(3)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("black-dragon"), Quantity(9)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("red-dragon"), Quantity(15)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("bowman"), Quantity(12)), gameMetadata)
                    event(CreatureAddedToArmy(armyId, CreatureId("behemoth"), Quantity(11)), gameMetadata)
                    event(CreatureRemovedFromArmy(armyId, CreatureId("behemoth"), Quantity(11)), gameMetadata)
                } When {
                    command(
                        AddCreatureToArmy(armyId = armyId, creatureId = CreatureId("phoenix"), quantity = Quantity(3)),
                        gameMetadata
                    )
                } Then {
                    resultMessagePayload(CommandHandlerResult.Success)
                    events(CreatureAddedToArmy(armyId = armyId, creatureId = CreatureId("phoenix"), quantity = Quantity(3)))
                }
            }
        }
    }
}
