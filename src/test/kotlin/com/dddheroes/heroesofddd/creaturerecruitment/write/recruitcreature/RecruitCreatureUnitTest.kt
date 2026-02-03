package com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature

import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruited
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ArmyId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.CreatureId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.axonTestFixture
import org.axonframework.test.fixture.configSlice
import org.junit.jupiter.api.*

internal class RecruitCreatureUnitTest {

    private lateinit var sliceUnderTest: AxonTestFixture;

    @BeforeEach
    fun beforeEach() {
        val sliceConfig = RecruitCreatureWriteSliceConfig()
        sliceUnderTest = axonTestFixture(
            configSlice {
                registerEntity(sliceConfig.recruitCreatureSliceState())
                registerCommandHandlingModule(sliceConfig.recruitCreatureSlice())
            }
        )
    }

    @AfterEach
    fun afterEach() {
        sliceUnderTest.stop()
    }

    @Test
    fun `given not built dwelling, when recruit creature, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest
            .given()
            .noPriorActivity()
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 1,
                    expectedCost = costPerTroop
                )
            )
            .then()
            .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Recruit creatures cannot exceed available creatures") }
    }

    @Test
    fun `given built but empty dwelling, when recruit creature, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 1,
                    expectedCost = costPerTroop
                )
            )
            .then()
            .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Recruit creatures cannot exceed available creatures") }
    }

    @Test
    fun `given dwelling with 1 creature, when recruit 1 creature, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = 1))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 1,
                    expectedCost = costPerTroop
                )
            )
            .then()
            .success()
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 1,
                    totalCost = costPerTroop
                ),
                CreatureAddedToArmy(
                    armyId = armyId,
                    creatureId = creatureId,
                    quantity = 1
                ),
                AvailableCreaturesChanged(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    changedBy = -1,
                    changedTo = 0
                )
            )
    }

    @Test
    fun `given dwelling with 2 creatures, when recruit 2 creatures, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val expectedCost = mapOf(ResourceType.GOLD to 6000, ResourceType.GEMS to 2)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 2, changedTo = 2))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 2,
                    expectedCost = expectedCost
                )
            )
            .then()
            .success()
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 2,
                    totalCost = expectedCost
                ),
                CreatureAddedToArmy(
                    armyId = armyId,
                    creatureId = creatureId,
                    quantity = 2
                ),
                AvailableCreaturesChanged(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    changedBy = -2,
                    changedTo = 0
                )
            )
    }

    @Test
    fun `given dwelling with 4 creatures, when recruit 3 creatures, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val expectedCost = mapOf(ResourceType.GOLD to 9000, ResourceType.GEMS to 3)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 3, changedTo = 3))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = 4))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 3,
                    expectedCost = expectedCost
                )
            )
            .then()
            .success()
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 3,
                    totalCost = expectedCost
                ),
                CreatureAddedToArmy(
                    armyId = armyId,
                    creatureId = creatureId,
                    quantity = 3
                ),
                AvailableCreaturesChanged(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    changedBy = -3,
                    changedTo = 1
                )
            )
    }

    @RepeatedTest(10)
    fun `given dwelling with 5 creatures, when recruit 6 creatures, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val expectedCost = mapOf(ResourceType.GOLD to 18000, ResourceType.GEMS to 6)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 5, changedTo = 5))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 6,
                    expectedCost = expectedCost
                )
            )
            .then()
            .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Recruit creatures cannot exceed available creatures") }
    }

    @Test
    fun `given dwelling with 1 creature, when recruit creature not from this dwelling, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val anotherCreatureId = CreatureId("black-dragon")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = 1))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = anotherCreatureId,
                    armyId = armyId,
                    quantity = 1,
                    expectedCost = costPerTroop
                )
            )
            .then()
            .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Recruit creatures cannot exceed available creatures") }
    }

    @Test
    fun `given dwelling with recruited all available creatures, when recruit creature, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val cost2 = mapOf(ResourceType.GOLD to 6000, ResourceType.GEMS to 2)
        val cost3 = mapOf(ResourceType.GOLD to 9000, ResourceType.GEMS to 3)
        val cost4 = mapOf(ResourceType.GOLD to 12000, ResourceType.GEMS to 4)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 3, changedTo = 3))
            .event(CreatureRecruited(dwellingId, creatureId, armyId, 2, cost2))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = -2, changedTo = 1))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 3, changedTo = 4))
            .event(CreatureRecruited(dwellingId, creatureId, armyId, 4, cost4))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = -4, changedTo = 0))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 3,
                    expectedCost = cost3
                )
            )
            .then()
            .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Recruit creatures cannot exceed available creatures") }
    }

    @Test
    fun `given dwelling with recruited some creatures and 1 left, when recruit 1 creature, then recruited`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val cost3 = mapOf(ResourceType.GOLD to 9000, ResourceType.GEMS to 3)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 4, changedTo = 4))
            .event(CreatureRecruited(dwellingId, creatureId, armyId, 3, cost3))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = -3, changedTo = 1))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 1,
                    expectedCost = costPerTroop
                )
            )
            .then()
            .success()
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 1,
                    totalCost = costPerTroop
                ),
                CreatureAddedToArmy(
                    armyId = armyId,
                    creatureId = creatureId,
                    quantity = 1
                ),
                AvailableCreaturesChanged(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    changedBy = -1,
                    changedTo = 0
                )
            )
    }

    @Test
    fun `given dwelling with 1 creature, when expected cost does not match actual cost, then exception`() {
        val dwellingId = DwellingId.random()
        val armyId = ArmyId.random()
        val creatureId = CreatureId("angel")
        val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)
        val wrongExpectedCost = mapOf(ResourceType.GOLD to 999999, ResourceType.GEMS to 0)

        sliceUnderTest
            .given()
            .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
            .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = 1))
            .`when`()
            .command(
                RecruitCreature(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    armyId = armyId,
                    quantity = 1,
                    expectedCost = wrongExpectedCost
                )
            )
            .then()
            .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Recruit cost cannot differ than expected cost") }
    }

    @Nested
    inner class ArmyCreatureTypesLimitTests {

        @Test
        fun `given empty army, when recruit creature, then recruited`() {
            val dwellingId = DwellingId.random()
            val armyId = ArmyId.random()
            val creatureId = CreatureId("angel")
            val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

            sliceUnderTest
                .given()
                .event(DwellingBuilt(dwellingId, creatureId, costPerTroop))
                .event(AvailableCreaturesChanged(dwellingId, creatureId, changedBy = 1, changedTo = 1))
                .`when`()
                .command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        armyId = armyId,
                        quantity = 1,
                        expectedCost = costPerTroop
                    )
                )
                .then()
                .success()
                .events(
                    CreatureRecruited(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        toArmy = armyId,
                        quantity = 1,
                        totalCost = costPerTroop
                    ),
                    CreatureAddedToArmy(
                        armyId = armyId,
                        creatureId = creatureId,
                        quantity = 1
                    ),
                    AvailableCreaturesChanged(
                        dwellingId = dwellingId,
                        creatureId = creatureId,
                        changedBy = -1,
                        changedTo = 0
                    )
                )
        }

        @Test
        fun `given army with 7 different creature types, when recruit new 8th creature type, then exception`() {
            val dwellingId = DwellingId.random()
            val armyId = ArmyId.random()
            val newCreatureId = CreatureId("black-dragon")
            val costPerTroop = mapOf(ResourceType.GOLD to 4000, ResourceType.GEMS to 2)

            sliceUnderTest
                .given()
                .event(DwellingBuilt(dwellingId, newCreatureId, costPerTroop))
                .event(AvailableCreaturesChanged(dwellingId, newCreatureId, changedBy = 1, changedTo = 1))
                // Simulate army already having 7 different creature types
                .event(CreatureAddedToArmy(armyId, CreatureId("angel"), 5))
                .event(CreatureAddedToArmy(armyId, CreatureId("griffin"), 10))
                .event(CreatureAddedToArmy(armyId, CreatureId("swordsman"), 20))
                .event(CreatureAddedToArmy(armyId, CreatureId("monk"), 8))
                .event(CreatureAddedToArmy(armyId, CreatureId("cavalier"), 6))
                .event(CreatureAddedToArmy(armyId, CreatureId("mage"), 4))
                .event(CreatureAddedToArmy(armyId, CreatureId("titan"), 2))
                .`when`()
                .command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = newCreatureId,
                        armyId = armyId,
                        quantity = 1,
                        expectedCost = costPerTroop
                    )
                )
                .then()
                .exceptionSatisfies { ex -> assertThat(ex).hasMessageContaining("Army cannot contain more than 7 different creature types") }
        }

        @Test
        fun `given army with 6 different creature types, when recruit new 7th creature type, then recruited`() {
            val dwellingId = DwellingId.random()
            val armyId = ArmyId.random()
            val newCreatureId = CreatureId("black-dragon")
            val costPerTroop = mapOf(ResourceType.GOLD to 4000, ResourceType.GEMS to 2)

            sliceUnderTest
                .given()
                .event(DwellingBuilt(dwellingId, newCreatureId, costPerTroop))
                .event(AvailableCreaturesChanged(dwellingId, newCreatureId, changedBy = 1, changedTo = 1))
                // Simulate army having 6 different creature types
                .event(CreatureAddedToArmy(armyId, CreatureId("angel"), 5))
                .event(CreatureAddedToArmy(armyId, CreatureId("griffin"), 10))
                .event(CreatureAddedToArmy(armyId, CreatureId("swordsman"), 20))
                .event(CreatureAddedToArmy(armyId, CreatureId("monk"), 8))
                .event(CreatureAddedToArmy(armyId, CreatureId("cavalier"), 6))
                .event(CreatureAddedToArmy(armyId, CreatureId("mage"), 4))
                .`when`()
                .command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = newCreatureId,
                        armyId = armyId,
                        quantity = 1,
                        expectedCost = costPerTroop
                    )
                )
                .then()
                .success()
                .events(
                    CreatureRecruited(
                        dwellingId = dwellingId,
                        creatureId = newCreatureId,
                        toArmy = armyId,
                        quantity = 1,
                        totalCost = costPerTroop
                    ),
                    CreatureAddedToArmy(
                        armyId = armyId,
                        creatureId = newCreatureId,
                        quantity = 1
                    ),
                    AvailableCreaturesChanged(
                        dwellingId = dwellingId,
                        creatureId = newCreatureId,
                        changedBy = -1,
                        changedTo = 0
                    )
                )
        }

        @Test
        fun `given army with 7 different creature types, when recruit more of existing creature, then recruited`() {
            val dwellingId = DwellingId.random()
            val armyId = ArmyId.random()
            val existingCreatureId = CreatureId("angel")
            val costPerTroop = mapOf(ResourceType.GOLD to 3000, ResourceType.GEMS to 1)

            sliceUnderTest
                .given()
                .event(DwellingBuilt(dwellingId, existingCreatureId, costPerTroop))
                .event(AvailableCreaturesChanged(dwellingId, existingCreatureId, changedBy = 2, changedTo = 2))
                // Simulate army already having 7 different creature types including the one we want to recruit
                .event(CreatureAddedToArmy(armyId, existingCreatureId, 5))
                .event(CreatureAddedToArmy(armyId, CreatureId("griffin"), 10))
                .event(CreatureAddedToArmy(armyId, CreatureId("swordsman"), 20))
                .event(CreatureAddedToArmy(armyId, CreatureId("monk"), 8))
                .event(CreatureAddedToArmy(armyId, CreatureId("cavalier"), 6))
                .event(CreatureAddedToArmy(armyId, CreatureId("mage"), 4))
                .event(CreatureAddedToArmy(armyId, CreatureId("titan"), 2))
                .`when`()
                .command(
                    RecruitCreature(
                        dwellingId = dwellingId,
                        creatureId = existingCreatureId,
                        armyId = armyId,
                        quantity = 2,
                        expectedCost = mapOf(ResourceType.GOLD to 6000, ResourceType.GEMS to 2)
                    )
                )
                .then()
                .success()
                .events(
                    CreatureRecruited(
                        dwellingId = dwellingId,
                        creatureId = existingCreatureId,
                        toArmy = armyId,
                        quantity = 2,
                        totalCost = mapOf(ResourceType.GOLD to 6000, ResourceType.GEMS to 2)
                    ),
                    CreatureAddedToArmy(
                        armyId = armyId,
                        creatureId = existingCreatureId,
                        quantity = 2
                    ),
                    AvailableCreaturesChanged(
                        dwellingId = dwellingId,
                        creatureId = existingCreatureId,
                        changedBy = -2,
                        changedTo = 0
                    )
                )
        }

    }
} 