package com.dddheroes.heroesofddd.shared.domain.valueobjects

@JvmInline
value class Quantity(val raw: Int) : Comparable<Quantity> {

    init {
        require(raw >= 0) { "Quantity cannot be negative" }
    }

    override fun compareTo(other: Quantity): Int = raw.compareTo(other.raw)

    operator fun plus(other: Quantity): Quantity = Quantity(raw + other.raw)

    operator fun minus(other: Quantity): Quantity = Quantity(raw - other.raw)

    operator fun times(multiplier: Int): Quantity = Quantity(raw * multiplier)

    operator fun div(divisor: Int): Quantity = Quantity(raw / divisor)

    companion object {
        fun zero(): Quantity = Quantity(0)
    }
}