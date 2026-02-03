package com.dddheroes.heroesofddd.shared.domain.valueobjects

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class QuantityTest {

    @Nested
    inner class Creation {

        @Test
        fun `should create quantity with zero value`() {
            val quantity = Quantity(0)

            assertThat(quantity.raw).isEqualTo(0)
        }

        @Test
        fun `should create quantity with positive value`() {
            val quantity = Quantity(42)

            assertThat(quantity.raw).isEqualTo(42)
        }

        @Test
        fun `should throw exception when creating quantity with negative value`() {
            assertThatThrownBy { Quantity(-1) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Quantity cannot be negative")
        }

        @Test
        fun `zero() should create quantity with value 0`() {
            val quantity = Quantity.zero()

            assertThat(quantity.raw).isEqualTo(0)
        }
    }

    @Nested
    inner class Comparison {

        @Test
        fun `should be equal when values are the same`() {
            val quantity1 = Quantity(5)
            val quantity2 = Quantity(5)

            assertThat(quantity1).isEqualTo(quantity2)
        }

        @Test
        fun `should not be equal when values differ`() {
            val quantity1 = Quantity(5)
            val quantity2 = Quantity(10)

            assertThat(quantity1).isNotEqualTo(quantity2)
        }

        @Test
        fun `should be less than greater quantity`() {
            val smaller = Quantity(5)
            val larger = Quantity(10)

            assertThat(smaller).isLessThan(larger)
            assertThat(smaller < larger).isTrue()
        }

        @Test
        fun `should be greater than smaller quantity`() {
            val smaller = Quantity(5)
            val larger = Quantity(10)

            assertThat(larger).isGreaterThan(smaller)
            assertThat(larger > smaller).isTrue()
        }

        @Test
        fun `should be less than or equal when equal`() {
            val quantity1 = Quantity(5)
            val quantity2 = Quantity(5)

            assertThat(quantity1 <= quantity2).isTrue()
            assertThat(quantity1 >= quantity2).isTrue()
        }
    }

    @Nested
    inner class Addition {

        @Test
        fun `should add two quantities`() {
            val quantity1 = Quantity(5)
            val quantity2 = Quantity(3)

            val result = quantity1 + quantity2

            assertThat(result).isEqualTo(Quantity(8))
        }

        @Test
        fun `should add zero quantity`() {
            val quantity = Quantity(5)

            val result = quantity + Quantity.zero()

            assertThat(result).isEqualTo(quantity)
        }
    }

    @Nested
    inner class Subtraction {

        @Test
        fun `should subtract smaller quantity from larger`() {
            val quantity1 = Quantity(10)
            val quantity2 = Quantity(3)

            val result = quantity1 - quantity2

            assertThat(result).isEqualTo(Quantity(7))
        }

        @Test
        fun `should subtract equal quantities resulting in zero`() {
            val quantity1 = Quantity(5)
            val quantity2 = Quantity(5)

            val result = quantity1 - quantity2

            assertThat(result).isEqualTo(Quantity.zero())
        }

        @Test
        fun `should throw exception when subtraction results in negative value`() {
            val quantity1 = Quantity(3)
            val quantity2 = Quantity(10)

            assertThatThrownBy { quantity1 - quantity2 }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Quantity cannot be negative")
        }
    }

    @Nested
    inner class Multiplication {

        @Test
        fun `should multiply by positive integer`() {
            val quantity = Quantity(5)

            val result = quantity * 3

            assertThat(result).isEqualTo(Quantity(15))
        }

        @Test
        fun `should multiply by zero resulting in zero`() {
            val quantity = Quantity(5)

            val result = quantity * 0

            assertThat(result).isEqualTo(Quantity.zero())
        }

        @Test
        fun `should multiply by one returning same value`() {
            val quantity = Quantity(5)

            val result = quantity * 1

            assertThat(result).isEqualTo(quantity)
        }

        @Test
        fun `should throw exception when multiplying by negative integer`() {
            val quantity = Quantity(5)

            assertThatThrownBy { quantity * -2 }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Quantity cannot be negative")
        }
    }

    @Nested
    inner class Division {

        @Test
        fun `should divide by positive integer`() {
            val quantity = Quantity(10)

            val result = quantity / 2

            assertThat(result).isEqualTo(Quantity(5))
        }

        @Test
        fun `should divide by one returning same value`() {
            val quantity = Quantity(10)

            val result = quantity / 1

            assertThat(result).isEqualTo(quantity)
        }

        @Test
        fun `should perform integer division truncating result`() {
            val quantity = Quantity(7)

            val result = quantity / 2

            assertThat(result).isEqualTo(Quantity(3))
        }

        @Test
        fun `should throw exception when dividing by zero`() {
            val quantity = Quantity(10)

            assertThatThrownBy { quantity / 0 }
                .isInstanceOf(ArithmeticException::class.java)
        }

        @Test
        fun `should throw exception when dividing by negative integer`() {
            val quantity = Quantity(10)

            assertThatThrownBy { quantity / -2 }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Quantity cannot be negative")
        }
    }
}
