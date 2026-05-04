package com.dddheroes.heroesofddd.shared.domain.identifiers

import java.util.*

@JvmInline
value class RecruitmentId(val raw: String) {

    init {
        require(raw.isNotBlank()) { "Recruitment ID cannot be empty" }
    }

    companion object {
        fun random() = RecruitmentId(UUID.randomUUID().toString())
    }

}
