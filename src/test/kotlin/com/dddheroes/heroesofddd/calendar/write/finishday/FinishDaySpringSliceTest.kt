package com.dddheroes.heroesofddd.calendar.write.finishday

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

@TestPropertySource(properties = ["slices.calendar.write.finishday.enabled=true"])
@HeroesAxonSpringBootTest
internal class FinishDaySpringSliceTest @Autowired constructor(
    private val sliceUnderTest: AxonTestFixture
) {

    private val gameId: String = UUID.randomUUID().toString()
    private val playerId: String = UUID.randomUUID().toString()

    private val calendarId = CalendarId.random()

    @Test
    fun `given day started, when finish that day, then DayFinished`() {
        sliceUnderTest.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } When {
                command(FinishDay(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(DayFinished(calendarId, month = Month(1), week = Week(1), day = Day(1)))
                allEventsHaveMetadata(gameMetadata)
            }
        }
    }

    @Test
    fun `given no day started, when finish day, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                noPriorActivity()
            } When {
                command(FinishDay(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Can only finish current day"))
                noEvents()
            }
        }
    }

    @Test
    fun `given day started, when finish different day, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } When {
                command(FinishDay(calendarId, month = Month(1), week = Week(1), day = Day(2)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Can only finish current day"))
                noEvents()
            }
        }
    }

    @Test
    fun `given day already finished, when finish same day again, then failure`() {
        sliceUnderTest.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
                event(DayFinished(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } When {
                command(FinishDay(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Failure("Can only finish current day"))
                noEvents()
            }
        }
    }

    @Test
    fun `given second day started, when finish second day, then DayFinished`() {
        sliceUnderTest.Scenario {
            Given {
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
                event(DayFinished(calendarId, month = Month(1), week = Week(1), day = Day(1)), gameMetadata)
                event(DayStarted(calendarId, month = Month(1), week = Week(1), day = Day(2)), gameMetadata)
            } When {
                command(FinishDay(calendarId, month = Month(1), week = Week(1), day = Day(2)), gameMetadata)
            } Then {
                resultMessagePayload(CommandHandlerResult.Success)
                events(DayFinished(calendarId, month = Month(1), week = Week(1), day = Day(2)))
            }
        }
    }

    private val gameMetadata = AxonMetadata.with("gameId", gameId)
        .and("playerId", playerId)

}
