package com.dddheroes.heroesofddd.astrologers.write

data class MonthWeek(val month: Int, val week: Int) {

    init {
        require(month >= 1) { "Month must be at least 1, got $month" }
        require(week in 1..4) { "Week must be between 1 and 4, got $week" }
    }

    val weekNumber: Int get() = ((month - 1) * 4) + week

}
