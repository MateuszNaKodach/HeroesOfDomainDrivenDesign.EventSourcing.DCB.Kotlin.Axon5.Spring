package com.dddheroes.heroesofddd.shared.domain.valueobjects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.util.*

@JvmInline
value class DwellingId @JsonCreator constructor(@get:JsonValue val raw: String) {

    init {
        require(raw.isNotBlank()) { "Dwelling id cannot be empty" }
    }

    companion object {
        fun random() = DwellingId(UUID.randomUUID().toString())
    }

}