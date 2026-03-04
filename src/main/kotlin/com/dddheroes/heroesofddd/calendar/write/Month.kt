package com.dddheroes.heroesofddd.calendar.write

@JvmInline
value class Month(val raw: Int) {

    init {
        require(raw >= 1) { "Month must be at least 1, got $raw" }
    }

}
