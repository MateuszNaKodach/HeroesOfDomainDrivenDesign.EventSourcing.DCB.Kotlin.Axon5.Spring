package com.dddheroes.heroesofddd.calendar.write

import java.util.*

@JvmInline
value class CalendarId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Calendar ID cannot be empty" }
    }

    companion object {
        fun random() = CalendarId(UUID.randomUUID().toString())
    }

}
