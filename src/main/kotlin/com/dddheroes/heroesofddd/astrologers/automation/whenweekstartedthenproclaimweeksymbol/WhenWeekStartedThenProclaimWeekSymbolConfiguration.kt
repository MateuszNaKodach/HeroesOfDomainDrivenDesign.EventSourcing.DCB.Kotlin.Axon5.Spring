package com.dddheroes.heroesofddd.astrologers.automation.whenweekstartedthenproclaimweeksymbol

import com.dddheroes.heroesofddd.astrologers.write.WeekSymbol
import com.dddheroes.heroesofddd.shared.domain.identifiers.CreatureId
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(
    prefix = "slices.astrologers.automation",
    name = ["whenweekstartedthenproclaimweeksymbol.enabled"]
)
@Configuration
private class WhenWeekStartedThenProclaimWeekSymbolConfiguration {

    @Bean
    fun weekSymbolCalculator(): WeekSymbolCalculator =
        WeekSymbolCalculator { _ -> WeekSymbol(weekOf = CreatureId("angel"), growth = (1..5).random()) }
}