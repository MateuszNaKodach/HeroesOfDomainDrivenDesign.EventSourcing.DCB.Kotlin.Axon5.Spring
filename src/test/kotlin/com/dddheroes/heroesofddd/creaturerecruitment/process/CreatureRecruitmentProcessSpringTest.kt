package com.dddheroes.heroesofddd.creaturerecruitment.process

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.armies.events.CreatureAddedToArmy
import com.dddheroes.heroesofddd.armies.write.addcreaturetoarmy.AddCreatureToArmy
import com.dddheroes.heroesofddd.creaturerecruitment.events.AvailableCreaturesChanged
import com.dddheroes.heroesofddd.creaturerecruitment.events.CreatureRecruitmentRequested
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures.IncreaseAvailableCreatures
import com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreature.RecruitCreature
import com.dddheroes.heroesofddd.creaturerecruitment.write.requestcreaturerecruitment.RequestCreatureRecruitment
import com.dddheroes.heroesofddd.resourcespool.events.ResourcesDeposited
import com.dddheroes.heroesofddd.resourcespool.write.depositresources.DepositResources
import com.dddheroes.heroesofddd.resourcespool.write.withdrawresources.WithdrawResources
import com.dddheroes.heroesofddd.shared.domain.identifiers.*
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import com.dddheroes.sdk.application.CommandHandlerResult.Success
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.*

@TestPropertySource(properties = [
    "axon.axonserver.enabled=true",
    "processes.creaturerecruitmentprocess.enabled=true",
    "slices.creaturerecruitment.write.requestcreaturerecruitment.enabled=true",
    "slices.creaturerecruitment.write.recruitcreature.enabled=true",
    "slices.creaturerecruitment.write.recruitcreaturedcb.enabled=false",
    "slices.creaturerecruitment.write.increaseavailablecreatures.enabled=true",
    "slices.resourcespool.write.withdrawresources.enabled=true",
    "slices.resourcespool.write.depositresources.enabled=true",
    "slices.armies.write.addcreaturetoarmy.enabled=true",
])
@HeroesAxonSpringBootTest
internal class CreatureRecruitmentProcessSpringTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    private val angel = CreatureId("angel")
    private val costPerTroop = Resources.of(ResourceType.GOLD to 50)

    @Test
    fun `happy path - withdraws resources, recruits creature, and adds to army`() {
        val recruitmentId = RecruitmentId.random()
        val dwellingId = DwellingId.random()
        val poolId = ResourcesPoolId(playerId)
        val armyId = ArmyId.random()
        val quantity = Quantity(2)
        val expectedCost = Resources.of(ResourceType.GOLD to 100) // 2 * 50g

        fixture.Scenario {
            Given {
                event(ResourcesDeposited(poolId, Resources.of(ResourceType.GOLD to 1000)), gameMetadata)
                event(DwellingBuilt(dwellingId, angel, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, angel, changedBy = 5, changedTo = Quantity(5)), gameMetadata)
            } When {
                command(
                    RequestCreatureRecruitment(
                        recruitmentId = recruitmentId,
                        dwellingId = dwellingId,
                        resourcesPoolId = poolId,
                        creatureId = angel,
                        armyId = armyId,
                        quantity = quantity,
                        expectedCost = expectedCost
                    ),
                    gameMetadata
                )
            } Then {
                await({
                    it.commandsSatisfy { commands ->
                        val payloads = commands.map { cmd -> cmd.payload() }
                        assertThat(payloads).contains(
                            WithdrawResources(poolId, expectedCost),
                            RecruitCreature(dwellingId, angel, armyId, quantity, expectedCost),
                            AddCreatureToArmy(armyId, angel, quantity)
                        )
                        assertThat(payloads.filterIsInstance<DepositResources>()
                            .filter { it.resourcesPoolId == poolId && it.resources == expectedCost }).isEmpty()
                    }
                }, Duration.ofSeconds(30))
            }
        }
    }

    @Test
    fun `recruit step fails - resources refunded, no creature added to army`() {
        val recruitmentId = RecruitmentId.random()
        val dwellingId = DwellingId.random()
        val poolId = ResourcesPoolId(playerId)
        val armyId = ArmyId.random()
        val wrongCost = Resources.of(ResourceType.GOLD to 999) // 2 troops × 50g = 100g, but 999g passed

        fixture.Scenario {
            Given {
                event(ResourcesDeposited(poolId, Resources.of(ResourceType.GOLD to 1000)), gameMetadata)
                event(DwellingBuilt(dwellingId, angel, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, angel, changedBy = 5, changedTo = Quantity(5)), gameMetadata)
            } When {
                command(
                    RequestCreatureRecruitment(
                        recruitmentId = recruitmentId,
                        dwellingId = dwellingId,
                        resourcesPoolId = poolId,
                        creatureId = angel,
                        armyId = armyId,
                        quantity = Quantity(2),
                        expectedCost = wrongCost
                    ),
                    gameMetadata
                )
            } Then {
                await({
                    it.commandsSatisfy { commands ->
                        val payloads = commands.map { cmd -> cmd.payload() }
                        assertThat(payloads).contains(
                            WithdrawResources(poolId, wrongCost),
                            DepositResources(poolId, wrongCost) // refund
                        )
                        assertThat(payloads.filterIsInstance<AddCreatureToArmy>()).isEmpty()
                    }
                }, Duration.ofSeconds(30))
            }
        }
    }

    @Test
    fun `army-add step fails - available creatures restored and resources refunded`() {
        val recruitmentId = RecruitmentId.random()
        val dwellingId = DwellingId.random()
        val poolId = ResourcesPoolId(playerId)
        val armyId = ArmyId.random()
        val quantity = Quantity(2)
        val expectedCost = Resources.of(ResourceType.GOLD to 100)

        fixture.Scenario {
            Given {
                event(ResourcesDeposited(poolId, Resources.of(ResourceType.GOLD to 1000)), gameMetadata)
                event(DwellingBuilt(dwellingId, angel, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, angel, changedBy = 5, changedTo = Quantity(5)), gameMetadata)
                // Pre-fill army with 7 distinct creatures (none is angel) to trigger the max-stacks invariant
                event(CreatureAddedToArmy(armyId, CreatureId("creature-1"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("creature-2"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("creature-3"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("creature-4"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("creature-5"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("creature-6"), Quantity(1)), gameMetadata)
                event(CreatureAddedToArmy(armyId, CreatureId("creature-7"), Quantity(1)), gameMetadata)
            } When {
                command(
                    RequestCreatureRecruitment(
                        recruitmentId = recruitmentId,
                        dwellingId = dwellingId,
                        resourcesPoolId = poolId,
                        creatureId = angel,
                        armyId = armyId,
                        quantity = quantity,
                        expectedCost = expectedCost
                    ),
                    gameMetadata
                )
            } Then {
                await({
                    it.commandsSatisfy { commands ->
                        val payloads = commands.map { cmd -> cmd.payload() }
                        // Withdraw and recruit were dispatched before army-add failed
                        assertThat(payloads).contains(
                            WithdrawResources(poolId, expectedCost),
                            RecruitCreature(dwellingId, angel, armyId, quantity, expectedCost)
                        )
                        // AddCreatureToArmy was attempted (triggers the max-7-stacks failure)
                        assertThat(payloads.filterIsInstance<AddCreatureToArmy>()
                            .filter { it.armyId == armyId && it.creatureId == angel }).isNotEmpty()
                        // Compensation: available creatures restored
                        val compensationStock = payloads.filterIsInstance<IncreaseAvailableCreatures>()
                            .filter { it.dwellingId == dwellingId && it.increaseBy == quantity }
                        assertThat(compensationStock).isNotEmpty()
                        // Compensation: resources refunded
                        assertThat(payloads).contains(DepositResources(poolId, expectedCost))
                    }
                }, Duration.ofSeconds(30))
            }
        }
    }

    @Test
    fun `insufficient resources - workflow halts after withdrawal fails, no further events`() {
        val recruitmentId = RecruitmentId.random()
        val dwellingId = DwellingId.random()
        val poolId = ResourcesPoolId(playerId)
        val armyId = ArmyId.random()
        val expectedCost = Resources.of(ResourceType.GOLD to 100)

        fixture.Scenario {
            Given {
                event(ResourcesDeposited(poolId, Resources.of(ResourceType.GOLD to 10)), gameMetadata) // only 10g, need 100g
                event(DwellingBuilt(dwellingId, angel, costPerTroop), gameMetadata)
                event(AvailableCreaturesChanged(dwellingId, angel, changedBy = 5, changedTo = Quantity(5)), gameMetadata)
            } When {
                command(
                    RequestCreatureRecruitment(
                        recruitmentId = recruitmentId,
                        dwellingId = dwellingId,
                        resourcesPoolId = poolId,
                        creatureId = angel,
                        armyId = armyId,
                        quantity = Quantity(2),
                        expectedCost = expectedCost
                    ),
                    gameMetadata
                )
            } Then {
                resultMessagePayload(Success)
                events(
                    CreatureRecruitmentRequested(
                        recruitmentId = recruitmentId,
                        dwellingId = dwellingId,
                        resourcesPoolId = poolId,
                        creatureId = angel,
                        armyId = armyId,
                        quantity = Quantity(2),
                        expectedCost = expectedCost
                    )
                )
            }
        }
    }
}
