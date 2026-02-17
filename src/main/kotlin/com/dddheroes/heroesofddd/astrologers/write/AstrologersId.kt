package com.dddheroes.heroesofddd.astrologers.write

import java.util.*

@JvmInline
value class AstrologersId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Astrologers ID cannot be empty" }
    }

    companion object {
        fun random() = AstrologersId(UUID.randomUUID().toString())
    }

}
