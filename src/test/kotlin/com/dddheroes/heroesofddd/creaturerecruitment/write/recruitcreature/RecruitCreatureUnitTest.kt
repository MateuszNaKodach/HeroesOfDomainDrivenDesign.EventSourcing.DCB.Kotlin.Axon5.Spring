package com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature

import com.dddheroes.heroesofddd.creaturerecruitment.UnitTestAxonApplication
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruited
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.Test
import java.util.*

internal class RecruitCreatureUnitTest {

    private val sliceUnderTest = AxonTestFixture.with(
        UnitTestAxonApplication.configurer(
            { registerStatefulCommandHandlingModule { RecruitCreatureWriteSliceConfig().recruitCreatureSlice() } },
            { axonServerEnabled = false }
        ))

    @Test
    fun `given not built dwelling, when recruit creature, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .exception(IllegalStateException::class.java, "Recruit creatures cannot exceed available creatures")
    }

    @Test
    fun `given built but empty dwelling, when recruit creature, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .exception(IllegalStateException::class.java, "Recruit creatures cannot exceed available creatures")
    }

    @Test
    fun `given dwelling with 1 creature, when recruit 1 creature, then recruited`() {
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 1,
                    totalCost = costPerTroop
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
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 2,
                    totalCost = expectedCost
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
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 3,
                    totalCost = expectedCost
                ),
                AvailableCreaturesChanged(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    changedBy = -3,
                    changedTo = 1
                )
            )
    }

    @Test
    fun `given dwelling with 5 creatures, when recruit 6 creatures, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .exception(IllegalStateException::class.java, "Recruit creatures cannot exceed available creatures")
    }

    @Test
    fun `given dwelling with 1 creature, when recruit creature not from this dwelling, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
        val anotherCreatureId = "black-dragon"
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
            .exception(IllegalStateException::class.java, "Recruit creatures cannot exceed available creatures")
    }

    @Test
    fun `given dwelling with recruited all available creatures, when recruit creature, then exception`() {
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .exception(IllegalStateException::class.java, "Recruit creatures cannot exceed available creatures")
    }

    @Test
    fun `given dwelling with recruited some creatures and 1 left, when recruit 1 creature, then recruited`() {
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .events(
                CreatureRecruited(
                    dwellingId = dwellingId,
                    creatureId = creatureId,
                    toArmy = armyId,
                    quantity = 1,
                    totalCost = costPerTroop
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
        val dwellingId = UUID.randomUUID().toString()
        val armyId = UUID.randomUUID().toString()
        val creatureId = "angel"
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
            .exception(IllegalStateException::class.java, "Recruit cost cannot differ than expected cost")
    }
} 