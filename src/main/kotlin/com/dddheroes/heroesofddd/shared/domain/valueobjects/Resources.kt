package com.dddheroes.heroesofddd.shared.domain.valueobjects

class Resources private constructor(val raw: Map<ResourceType, Quantity>) {

    fun isEmpty(): Boolean = raw.values.all { it.raw == 0 }

    operator fun plus(other: Resources): Resources =
        resources(raw.mapValues { (type, amount) -> amount + other.amountOf(type) })

    fun plus(type: ResourceType, quantity: Quantity): Resources =
        resources(raw + (type to amountOf(type) + quantity))

    operator fun minus(other: Resources): Resources =
        resources(raw.mapValues { (type, amount) -> amount - other.amountOf(type) })

    fun minus(type: ResourceType, quantity: Quantity): Resources =
        resources(raw + (type to amountOf(type) - quantity))

    operator fun times(multiplier: Int): Resources =
        resources(raw.mapValues { (_, amount) -> amount * multiplier })

    operator fun times(quantity: Quantity): Resources = times(quantity.raw)

    fun amountOf(type: ResourceType): Quantity = raw.getValue(type)

    operator fun contains(other: Resources): Boolean =
        other.raw.all { (type, amount) -> amountOf(type) >= amount }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Resources) return false
        return raw == other.raw
    }

    override fun hashCode(): Int = raw.hashCode()

    override fun toString(): String = "Resources($raw)"

    companion object {
        fun empty(): Resources = resources(emptyMap())

        fun of(vararg pairs: Pair<ResourceType, Int>): Resources = of(pairs.toMap())

        fun of(raw: Map<ResourceType, Int>): Resources = resources(
            raw.map { (key, value) -> key to Quantity(value) }.toMap()
        )

        @JvmName("fromRaw")
        fun of(raw: Map<String, Int>): Resources = resources(
            raw.map { (key, value) -> ResourceType.from(key) to Quantity(value) }.toMap()
        )

        private fun normalized(partial: Map<ResourceType, Quantity>): Map<ResourceType, Quantity> =
            ResourceType.entries.associateWith { partial.getOrDefault(it, Quantity.zero()) }

        private fun resources(partial: Map<ResourceType, Quantity>): Resources = Resources(normalized(partial))
    }
}
