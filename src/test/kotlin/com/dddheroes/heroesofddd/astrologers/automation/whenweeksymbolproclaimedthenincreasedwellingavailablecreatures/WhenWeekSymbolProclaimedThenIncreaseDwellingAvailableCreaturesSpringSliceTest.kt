package com.dddheroes.heroesofddd.astrologers.automation.whenweeksymbolproclaimedthenincreasedwellingavailablecreatures

import com.dddheroes.heroesofddd.astrologers.events.WeekSymbolProclaimed
import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.creaturerecruitment.events.DwellingBuilt
import com.dddheroes.heroesofddd.creaturerecruitment.write.increaseavailablecreatures.IncreaseAvailableCreatures
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import com.dddheroes.heroesofddd.shared.domain.identifiers.DwellingId
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Quantity
import com.dddheroes.heroesofddd.shared.domain.valueobjects.ResourceType
import com.dddheroes.heroesofddd.shared.domain.valueobjects.Resources
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.extensions.spring.test.AxonSpringBootTest
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.Given
import org.axonframework.test.fixture.Scenario
import org.axonframework.test.fixture.Then
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(
    properties = [
        "slices.astrologers.automation.whenweeksymbolproclaimedthenincreasedwellingavailablecreatures.enabled=true",
        "slices.creaturerecruitment.write.increaseavailablecreatures.enabled=true"
    ]
)
@AxonSpringBootTest
internal class WhenWeekSymbolProclaimedThenIncreaseDwellingAvailableCreaturesSpringSliceTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val astrologersId = AstrologersId(gameId)

    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    private val costPerTroop = Resources.of(ResourceType.GOLD to 1000)

    @Test
    fun `when WeekSymbolProclaimed, then increase available creatures for matching dwellings only`() {
        val angelDwelling1 = DwellingId.random()
        val angelDwelling2 = DwellingId.random()
        val titanDwelling = DwellingId.random()

        fixture.Scenario {
            Given {
                event(DwellingBuilt(angelDwelling1, CreatureId("angel"), costPerTroop), gameMetadata)
                event(DwellingBuilt(angelDwelling2, CreatureId("angel"), costPerTroop), gameMetadata)
                event(DwellingBuilt(titanDwelling, CreatureId("titan"), costPerTroop), gameMetadata)
                event(
                    WeekSymbolProclaimed(astrologersId, month = 1, week = 1, weekOf = CreatureId("angel"), growth = 3),
                    gameMetadata
                )
            } Then {
                await({
                    it.commandsSatisfy { commands ->
                        val payloads = commands.map { cmd -> cmd.payload() }

                        assertThat(payloads).contains(
                            IncreaseAvailableCreatures(angelDwelling1, CreatureId("angel"), Quantity(3)),
                            IncreaseAvailableCreatures(angelDwelling2, CreatureId("angel"), Quantity(3))
                        )
                    }
                })
            }
        }
    }

    @Test
    fun `when WeekSymbolProclaimed, then increase only dwellings built before the proclamation`() {
        val angelDwelling1 = DwellingId.random()
        val angelDwelling2 = DwellingId.random()

        fixture.Scenario {
            Given {
                // Dwelling 1 built before week 1
                event(DwellingBuilt(angelDwelling1, CreatureId("angel"), costPerTroop), gameMetadata)
                // Week 1: growth 1 — only dwelling 1 exists
                event(
                    WeekSymbolProclaimed(astrologersId, month = 1, week = 1, weekOf = CreatureId("angel"), growth = 1),
                    gameMetadata
                )
                // Dwelling 2 built after week 1
                event(DwellingBuilt(angelDwelling2, CreatureId("angel"), costPerTroop), gameMetadata)
                // Week 2: growth 2 — both dwellings exist
                event(
                    WeekSymbolProclaimed(astrologersId, month = 1, week = 2, weekOf = CreatureId("angel"), growth = 2),
                    gameMetadata
                )
            } Then {
                await({
                    it.commandsSatisfy { commands ->
                        val payloads = commands.map { cmd -> cmd.payload() }

                        assertThat(payloads).contains(
                            // Week 1: only dwelling 1 was built
                            IncreaseAvailableCreatures(angelDwelling1, CreatureId("angel"), Quantity(1)),
                            // Week 2: both dwellings were built
                            IncreaseAvailableCreatures(angelDwelling1, CreatureId("angel"), Quantity(2)),
                            IncreaseAvailableCreatures(angelDwelling2, CreatureId("angel"), Quantity(2))
                        )
                    }
                })
            }
        }
    }
}
