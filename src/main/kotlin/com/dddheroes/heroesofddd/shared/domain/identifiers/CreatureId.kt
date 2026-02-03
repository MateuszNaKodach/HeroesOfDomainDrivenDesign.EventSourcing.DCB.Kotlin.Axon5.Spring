package com.dddheroes.heroesofddd.shared.domain.identifiers

@JvmInline
value class CreatureId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Creature ID cannot be empty" }
    }

}
