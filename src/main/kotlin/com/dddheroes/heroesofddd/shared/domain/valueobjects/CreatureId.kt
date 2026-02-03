package com.dddheroes.heroesofddd.shared.domain.valueobjects

@JvmInline
value class CreatureId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Creature id cannot be empty" }
    }

}
