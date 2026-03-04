package com.dddheroes.heroesofddd.calendar.write

@JvmInline
value class Day(val raw: Int) {

    init {
        require(raw in 1..7) { "Day must be between 1 and 7, got $raw" }
    }

    val isLast: Boolean get() = raw == 7

    fun next(): Day = Day((raw % 7) + 1)

}
