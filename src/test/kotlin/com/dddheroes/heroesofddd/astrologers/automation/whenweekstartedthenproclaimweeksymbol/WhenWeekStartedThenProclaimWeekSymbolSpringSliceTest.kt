package com.dddheroes.heroesofddd.astrologers.automation.whenweekstartedthenproclaimweeksymbol

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.astrologers.write.MonthWeek
import com.dddheroes.heroesofddd.astrologers.write.WeekSymbol
import com.dddheroes.heroesofddd.astrologers.write.proclaimweeksymbol.ProclaimWeekSymbol
import com.dddheroes.heroesofddd.calendar.events.DayStarted
import com.dddheroes.heroesofddd.calendar.write.CalendarId
import com.dddheroes.heroesofddd.calendar.write.Day
import com.dddheroes.heroesofddd.calendar.write.Month
import com.dddheroes.heroesofddd.calendar.write.Week
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.Given
import org.axonframework.test.fixture.Scenario
import org.axonframework.test.fixture.Then
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.*

@TestPropertySource(
    properties = [
        "slices.astrologers.automation.whenweekstartedthenproclaimweeksymbol.enabled=true",
        "slices.astrologers.write.proclaimweeksymbol.enabled=true"
    ]
)
@HeroesAxonSpringBootTest
internal class WhenWeekStartedThenProclaimWeekSymbolSpringSliceTest @Autowired constructor(
    private val fixture: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()
    private val calendarId = CalendarId(gameId)

    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun deterministicWeekSymbolCalculator(): WeekSymbolCalculator =
            WeekSymbolCalculator { _ -> WeekSymbol(weekOf = CreatureId("angel"), growth = 5) }
    }

    @Test
    fun `when DayStarted for first day of the week, then ProclaimWeekSymbol command dispatched`() {
        val expectedCommand = ProclaimWeekSymbol(
            astrologersId = AstrologersId(calendarId.raw),
            week = MonthWeek(month = 1, week = 1),
            symbol = WeekSymbol(weekOf = CreatureId("angel"), growth = 5)
        )

        fixture.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } Then {
                await({ it.commands(expectedCommand) }, Duration.ofSeconds(5))
            }
        }
    }

    @Test
    fun `when DayStarted for non-first day of the week, then no commands dispatched`() {
        fixture.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(3)), gameMetadata)
            } Then {
                await({ it.noCommands() }, Duration.ofSeconds(5))
            }
        }
    }
}
