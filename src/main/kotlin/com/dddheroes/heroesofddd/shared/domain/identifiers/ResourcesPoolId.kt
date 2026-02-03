package com.dddheroes.heroesofddd.shared.domain.identifiers

import java.util.*

@JvmInline
value class ResourcesPoolId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Resources Pool ID cannot be empty" }
    }

    companion object {
        fun random() = ResourcesPoolId(UUID.randomUUID().toString())
    }

}