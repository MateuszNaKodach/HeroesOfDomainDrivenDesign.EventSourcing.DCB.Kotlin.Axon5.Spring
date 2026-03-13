package com.dddheroes.heroesofddd.calendar.write.startday

import com.dddheroes.heroesofddd.HeroesAxonSpringBootTest
import com.dddheroes.heroesofddd.calendar.events.DayFinished
import com.dddheroes.heroesofddd.calendar.events.DayStarted
import com.dddheroes.heroesofddd.calendar.write.CalendarId
import com.dddheroes.heroesofddd.calendar.write.Day
import com.dddheroes.heroesofddd.calendar.write.Month
import com.dddheroes.heroesofddd.calendar.write.Week
import com.dddheroes.sdk.application.CommandHandlerResult
import org.axonframework.extensions.kotlin.AxonMetadata
import org.axonframework.test.fixture.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.calendar.write.startday.enabled=true"])
@HeroesAxonSpringBootTest
internal class StartDaySpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()

    private val calendarId = CalendarId.random()

    @Test
    fun `given no prior activity, when start first day, then DayStarted`() {
        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(StartDay(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given previous day finished, when start next day, then DayStarted`() {
        sliceUnderTest.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
                event(DayFinished(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } When {
                command(StartDay(calendarId, month = Month(1), week = Week(1), day = Day(2)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(2)))
            }
        }
    }

    @Test
    fun `given previous day finished, when start day skipping, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
                event(DayFinished(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } When {
                command(StartDay(calendarId, month = Month(1), week = Week(1), day = Day(3)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Cannot skip days"))
                noEvents()
            }
        }
    }

    @Test
    fun `given last day of week finished, when start first day of next week, then DayStarted`() {
        sliceUnderTest.Scenario {
            Given {
                (1..7).forEach { day ->
                    event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(day)), gameMetadata)
                    event(DayFinished(calendarId, month = Month(1), week = Week(1), day = Day(day)), gameMetadata)
                }
            } When {
                command(StartDay(calendarId, month = Month(1), week = Week(2), day = Day(1)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(DayStarted(calendarId, month = Month(1), week = Week(2), day = Day(1)))
            }
        }
    }

    @Test
    fun `given last day of month finished, when start first day of next month, then DayStarted`() {
        sliceUnderTest.Scenario {
            Given {
                (1..4).forEach { week ->
                    (1..7).forEach { day ->
                        event(DayStarted(calendarId, month = Month(1), week = Week(week), day = Day(day)), gameMetadata)
                        event(DayFinished(calendarId, month = Month(1), week = Week(week), day = Day(day)), gameMetadata)
                    }
                }
            } When {
                command(StartDay(calendarId, month = Month(2), week = Week(1), day = Day(1)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(DayStarted(calendarId, month = Month(2), week = Week(1), day = Day(1)))
            }
        }
    }

    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

}
