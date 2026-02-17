package com.dddheroes.heroesofddd.astrologers.write

data class MonthWeek(val month: Int, val week: Int) {

    val weekNumber: Int get() = ((month - 1) * 4) + week

}
