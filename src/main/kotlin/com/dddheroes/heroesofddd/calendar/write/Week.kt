package com.dddheroes.heroesofddd.calendar.write

@JvmInline
value class Week(val raw: Int) {

    init {
        require(raw in 1..4) { "Week must be between 1 and 4, got $raw" }
    }

    val isLast: Boolean get() = raw == 4

    fun next(): Week = Week((raw % 4) + 1)

}
