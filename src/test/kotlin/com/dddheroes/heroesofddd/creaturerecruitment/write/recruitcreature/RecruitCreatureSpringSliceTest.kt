package com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruited
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.identifiers.ArmyId
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.creaturerecruitment.write.recruitcreature.enabled=true"])
@HeroesAxonSpringBootTest
internal class RecruitCreatureSpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    @Test
    fun `given not built dwelling, when recruit creature, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(1),
                        expectedCost = costPerTroop
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Recruit creatures cannot exceed available creatures"))
            }
        }
    }

    @Test
    fun `given built but empty dwelling, when recruit creature, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(1),
                        expectedCost = costPerTroop
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Recruit creatures cannot exceed available creatures"))
            }
        }
    }

    @Test
    fun `given dwelling with 1 creature, when recruit 1 creature, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = Quantity(1)), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(1),
                        expectedCost = costPerTroop
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(
                    CreatureRecruited(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        toArmy = armyId,
                        quantity = Quantity(1),
                        totalCost = costPerTroop
                    )
                )
            }
        }
    }

    @Test
    fun `given dwelling with 2 creatures, when recruit 2 creatures, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val expectedCost = Resources.of(ResourceType.GOLD to 6000, ResourceType.GEMS to 2)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 2, changedTo = Quantity(2)), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(2),
                        expectedCost = expectedCost
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(
                    CreatureRecruited(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        toArmy = armyId,
                        quantity = Quantity(2),
                        totalCost = expectedCost
                    )
                )
            }
        }
    }

    @Test
    fun `given dwelling with 4 creatures, when recruit 3 creatures, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val expectedCost = Resources.of(ResourceType.GOLD to 9000, ResourceType.GEMS to 3)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 3, changedTo = Quantity(3)), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = Quantity(4)), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(3),
                        expectedCost = expectedCost
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(
                    CreatureRecruited(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        toArmy = armyId,
                        quantity = Quantity(3),
                        totalCost = expectedCost
                    )
                )
            }
        }
    }

    @RepeatedTest(10)
    fun `given dwelling with 5 creatures, when recruit 6 creatures, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val expectedCost = Resources.of(ResourceType.GOLD to 18000, ResourceType.GEMS to 6)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 5, changedTo = Quantity(5)), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(6),
                        expectedCost = expectedCost
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Recruit creatures cannot exceed available creatures"))
            }
        }
    }

    @Test
    fun `given dwelling with 1 creature, when recruit creature not from this dwelling, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val anotherCreatureId = CreatureId("black-dragon")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = Quantity(1)), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = anotherCreatureId,
                        armyId = armyId,
                        quantity = Quantity(1),
                        expectedCost = costPerTroop
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Recruit creatures cannot exceed available creatures"))
            }
        }
    }

    @Test
    fun `given dwelling with recruited some creatures and 1 left, when recruit 1 creature, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val cost3 = Resources.of(ResourceType.GOLD to 9000, ResourceType.GEMS to 3)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 4, changedTo = Quantity(4)), gameMetadata)
                // prior recruitment tracked via CreatureRecruited (availableCreatures: 4 - 3 = 1)
                event(CreatureRecruited(dwellingId, creatureId, armyId, Quantity(3), cost3), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(1),
                        expectedCost = costPerTroop
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(
                    CreatureRecruited(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        toArmy = armyId,
                        quantity = Quantity(1),
                        totalCost = costPerTroop
                    )
                )
            }
        }
    }

    @Test
    fun `given dwelling with recruited all available creatures, when recruit creature, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val cost3 = Resources.of(ResourceType.GOLD to 9000, ResourceType.GEMS to 3)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 3, changedTo = Quantity(3)), gameMetadata)
                // prior recruitment depleted all creatures
                event(CreatureRecruited(dwellingId, creatureId, armyId, Quantity(3), cost3), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(1),
                        expectedCost = costPerTroop
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Recruit creatures cannot exceed available creatures"))
            }
        }
    }

    @Test
    fun `given dwelling with 1 creature, when expected cost does not match actual cost, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = Resources.of(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val wrongExpectedCost = Resources.of(ResourceType.GOLD to 999999, ResourceType.GEMS to 0)

        sliceUnderTest.Scenario {
            Given {
                event(DwellingBuilt(dwellingId, creatureId, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = Quantity(1)), gameMetadata)
            } When {
                command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = Quantity(1),
                        expectedCost = wrongExpectedCost
                    ), gameMetadata
                )
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Recruit cost cannot differ than expected cost"))
            }
        }
    }
}
