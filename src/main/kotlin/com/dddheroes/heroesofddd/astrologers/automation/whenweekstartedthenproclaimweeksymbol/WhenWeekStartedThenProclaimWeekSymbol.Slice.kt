package com.dddheroes.heroesofddd.astrologers.automation.whenweekstartedthenproclaimweeksymbol

import com.dddheroes.heroesofddd.astrologers.write.AstrologersId
import com.dddheroes.heroesofddd.astrologers.write.MonthWeek
import com.dddheroes.heroesofddd.astrologers.write.proclaimweeksymbol.ProclaimWeekSymbol
import com.dddheroes.heroesofddd.calendar.events.DayStarted
import com.dddheroes.heroesofddd.shared.application.GameMetadata
import com.dddheroes.heroesofddd.shared.domain.identifiers.GameId
import com.dddheroes.heroesofddd.shared.domain.identifiers.PlayerId
import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.core.annotation.MetadataValue
import org.axonframework.messaging.eventhandling.annotation.EventHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private const val FIRST_DAY_OF_THE_WEEK = 1

@ConditionalOnProperty(
    prefix = "slices.astrologers.automation",
    name = ["whenweekstartedthenproclaimweeksymbol.enabled"]
)
@Component
private class WhenWeekStartedThenProclaimWeekSymbolProcessor(
    private val commandGateway: CommandGateway,
    private val weekSymbolCalculator: WeekSymbolCalculator
) {

    @EventHandler
    fun react(
        event: DayStarted,
        @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String,
        @MetadataValue(GameMetadata.PLAYER_ID_KEY) playerId: String
    ) {
        if (event.day == FIRST_DAY_OF_THE_WEEK) {
            val weekSymbol = weekSymbolCalculator(MonthWeek(event.month, event.week))
            val command = ProclaimWeekSymbol(
                astrologersId = AstrologersId(event.calendarId.raw),
                week = MonthWeek(event.month, event.week),
                symbol = weekSymbol
            )
            val metadata = GameMetadata.with(GameId(gameId), PlayerId(playerId))
            commandGateway.send(command, metadata)
        }
    }
}
