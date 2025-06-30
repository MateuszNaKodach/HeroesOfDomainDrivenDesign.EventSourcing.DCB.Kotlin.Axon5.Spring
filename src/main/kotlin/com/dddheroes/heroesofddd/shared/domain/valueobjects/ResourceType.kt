package com.dddheroes.heroesofddd.shared.domain.valueobjects

enum class ResourceType {
    GOLD,
    WOOD,
    ORE,
    MERCURY,
    SULFUR,
    CRYSTAL,
    GEMS;

    companion object {
        fun from(name: String): ResourceType = entries.find { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException("No enum constant ${ResourceType::class.java.canonicalName}.$name")
    }
} 