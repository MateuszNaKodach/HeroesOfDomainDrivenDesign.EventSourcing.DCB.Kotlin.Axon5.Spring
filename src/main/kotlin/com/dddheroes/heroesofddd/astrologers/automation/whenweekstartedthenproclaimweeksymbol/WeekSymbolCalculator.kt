package com.dddheroes.heroesofddd.astrologers.automation.whenweekstartedthenproclaimweeksymbol

import com.dddheroes.heroesofddd.astrologers.write.MonthWeek
import com.dddheroes.heroesofddd.astrologers.write.WeekSymbol

fun interface WeekSymbolCalculator : (MonthWeek) -> WeekSymbol