package com.dddheroes.heroesofddd.astrologers.write.proclaimweeksymbol

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.astrologers.events.WeekSymbolProclaimed
import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.astrologers.write.MonthWeek
import com.dddheroes.heroesofddd.astrologers.write.WeekSymbol
import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.springTestFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.astrologers.write.proclaimweeksymbol.enabled=true"])
@HeroesAxonSpringBootTest
internal class ProclaimWeekSymbolSpringSliceTest @Autowired constructor(configuration: AxonConfiguration) {

    private val sliceUnderTest: AxonTestFixture = springTestFixture(configuration)

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()

    private val astrologersId = AstrologersId.random()
    private val creatureId = CreatureId("angel")

    @Test
    fun `given no prior activity, when proclaim week symbol, then WeekSymbolProclaimed`() {
        val week = MonthWeek(month = 1, week = 1)
        val symbol = WeekSymbol(weekOf = creatureId, growth = 3)

        sliceUnderTest.given()
            .noPriorActivity()
            .`when`()
            .command(ProclaimWeekSymbol(astrologersId, week, symbol), gameMetadata)
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(
                WeekSymbolProclaimed(
                    astrologersId = astrologersId,
                    month = 1,
                    week = 1,
                    weekOf = creatureId,
                    growth = 3
                )
            )
    }

    @Test
    fun `given WeekSymbolProclaimed for week 1, when proclaim for week 2, then WeekSymbolProclaimed`() {
        val firstProclamation = WeekSymbolProclaimed(
            astrologersId = astrologersId,
            month = 1,
            week = 1,
            weekOf = creatureId,
            growth = 3
        )

        sliceUnderTest.given()
            .events(firstProclamation)
            .`when`()
            .command(
                ProclaimWeekSymbol(
                    astrologersId,
                    MonthWeek(month = 1, week = 2),
                    WeekSymbol(weekOf = creatureId, growth = 5)
                ),
                gameMetadata
            )
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(
                WeekSymbolProclaimed(
                    astrologersId = astrologersId,
                    month = 1,
                    week = 2,
                    weekOf = creatureId,
                    growth = 5
                )
            )
    }

    @Test
    fun `given WeekSymbolProclaimed for month 1, when proclaim for month 2, then WeekSymbolProclaimed`() {
        val firstProclamation = WeekSymbolProclaimed(
            astrologersId = astrologersId,
            month = 1,
            week = 4,
            weekOf = creatureId,
            growth = 3
        )

        sliceUnderTest.given()
            .events(firstProclamation)
            .`when`()
            .command(
                ProclaimWeekSymbol(
                    astrologersId,
                    MonthWeek(month = 2, week = 1),
                    WeekSymbol(weekOf = creatureId, growth = 2)
                ),
                gameMetadata
            )
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(
                WeekSymbolProclaimed(
                    astrologersId = astrologersId,
                    month = 2,
                    week = 1,
                    weekOf = creatureId,
                    growth = 2
                )
            )
    }

    @Test
    fun `given WeekSymbolProclaimed for week 1, when proclaim for same week, then failure`() {
        val firstProclamation = WeekSymbolProclaimed(
            astrologersId = astrologersId,
            month = 1,
            week = 1,
            weekOf = creatureId,
            growth = 3
        )

        sliceUnderTest.given()
            .events(firstProclamation)
            .`when`()
            .command(
                ProclaimWeekSymbol(
                    astrologersId,
                    MonthWeek(month = 1, week = 1),
                    WeekSymbol(weekOf = CreatureId("imp"), growth = 2)
                ),
                gameMetadata
            )
            .then()
            .resultMessagePayload(CommandHandlerResult.Failure("Only one symbol can be proclaimed per week"))
            .noEvents()
    }

    @Test
    fun `given WeekSymbolProclaimed for week 2, when proclaim for week 1, then failure`() {
        val firstProclamation = WeekSymbolProclaimed(
            astrologersId = astrologersId,
            month = 1,
            week = 2,
            weekOf = creatureId,
            growth = 3
        )

        sliceUnderTest.given()
            .events(firstProclamation)
            .`when`()
            .command(
                ProclaimWeekSymbol(
                    astrologersId,
                    MonthWeek(month = 1, week = 1),
                    WeekSymbol(weekOf = CreatureId("imp"), growth = 2)
                ),
                gameMetadata
            )
            .then()
            .resultMessagePayload(CommandHandlerResult.Failure("Only one symbol can be proclaimed per week"))
            .noEvents()
    }

    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

}
