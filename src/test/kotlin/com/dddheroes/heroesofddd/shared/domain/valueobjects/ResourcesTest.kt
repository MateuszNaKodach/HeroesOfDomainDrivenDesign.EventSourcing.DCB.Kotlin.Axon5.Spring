package com.dddheroes.heroesofddd.shared.domain.valueobjects

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ResourcesTest {

    @Nested
    inner class Creation {

        @Test
        fun `empty() should create resources with all types at zero`() {
            val resources = Resources.empty()

            assertThat(resources.isEmpty()).isTrue()
            ResourceType.entries.forEach { type ->
                assertThat(resources.amountOf(type)).isEqualTo(Quantity.zero())
            }
        }

        @Test
        fun `of(vararg) should create resources from pairs`() {
            val resources = Resources.of(
                ResourceType.GOLD to 100,
                ResourceType.WOOD to 10
            )

            assertThat(resources.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(100))
            assertThat(resources.amountOf(ResourceType.WOOD)).isEqualTo(Quantity(10))
            assertThat(resources.amountOf(ResourceType.ORE)).isEqualTo(Quantity.zero())
        }

        @Test
        fun `of(Map ResourceType Int) should create resources from map`() {
            val resources = Resources.of(
                mapOf(
                    ResourceType.GOLD to 500,
                    ResourceType.GEMS to 5
                )
            )

            assertThat(resources.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(500))
            assertThat(resources.amountOf(ResourceType.GEMS)).isEqualTo(Quantity(5))
            assertThat(resources.amountOf(ResourceType.MERCURY)).isEqualTo(Quantity.zero())
        }

        @Test
        fun `of(Map String Int) should create resources from string map`() {
            val resources = Resources.of(
                mapOf(
                    "GOLD" to 200,
                    "crystal" to 3
                )
            )

            assertThat(resources.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(200))
            assertThat(resources.amountOf(ResourceType.CRYSTAL)).isEqualTo(Quantity(3))
        }

        @Test
        fun `of(Map String Int) should throw for invalid resource type`() {
            assertThatThrownBy {
                Resources.of(mapOf("INVALID" to 100))
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class IsEmpty {

        @Test
        fun `should return true for empty resources`() {
            val resources = Resources.empty()

            assertThat(resources.isEmpty()).isTrue()
        }

        @Test
        fun `should return true when all values are zero`() {
            val resources = Resources.of(
                ResourceType.GOLD to 0,
                ResourceType.WOOD to 0
            )

            assertThat(resources.isEmpty()).isTrue()
        }

        @Test
        fun `should return false when any value is non-zero`() {
            val resources = Resources.of(ResourceType.GOLD to 1)

            assertThat(resources.isEmpty()).isFalse()
        }
    }

    @Nested
    inner class AmountOf {

        @Test
        fun `should return quantity for specified resource type`() {
            val resources = Resources.of(
                ResourceType.GOLD to 100,
                ResourceType.WOOD to 50
            )

            assertThat(resources.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(100))
            assertThat(resources.amountOf(ResourceType.WOOD)).isEqualTo(Quantity(50))
        }

        @Test
        fun `should return zero for unspecified resource type`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            assertThat(resources.amountOf(ResourceType.SULFUR)).isEqualTo(Quantity.zero())
        }
    }

    @Nested
    inner class Addition {

        @Test
        fun `should add two resources together`() {
            val resources1 = Resources.of(ResourceType.GOLD to 100, ResourceType.WOOD to 10)
            val resources2 = Resources.of(ResourceType.GOLD to 50, ResourceType.ORE to 5)

            val result = resources1 + resources2

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(150))
            assertThat(result.amountOf(ResourceType.WOOD)).isEqualTo(Quantity(10))
            assertThat(result.amountOf(ResourceType.ORE)).isEqualTo(Quantity(5))
        }

        @Test
        fun `should add empty resources without change`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            val result = resources + Resources.empty()

            assertThat(result).isEqualTo(resources)
        }

        @Test
        fun `should add specific resource type and quantity`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            val result = resources.plus(ResourceType.GOLD, Quantity(50))

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(150))
        }

        @Test
        fun `should add new resource type`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            val result = resources.plus(ResourceType.WOOD, Quantity(25))

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(100))
            assertThat(result.amountOf(ResourceType.WOOD)).isEqualTo(Quantity(25))
        }
    }

    @Nested
    inner class Subtraction {

        @Test
        fun `should subtract resources`() {
            val resources1 = Resources.of(ResourceType.GOLD to 100, ResourceType.WOOD to 20)
            val resources2 = Resources.of(ResourceType.GOLD to 30, ResourceType.WOOD to 5)

            val result = resources1 - resources2

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(70))
            assertThat(result.amountOf(ResourceType.WOOD)).isEqualTo(Quantity(15))
        }

        @Test
        fun `should subtract to zero`() {
            val resources1 = Resources.of(ResourceType.GOLD to 100)
            val resources2 = Resources.of(ResourceType.GOLD to 100)

            val result = resources1 - resources2

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity.zero())
        }

        @Test
        fun `should throw when subtraction results in negative`() {
            val resources1 = Resources.of(ResourceType.GOLD to 50)
            val resources2 = Resources.of(ResourceType.GOLD to 100)

            assertThatThrownBy { resources1 - resources2 }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Quantity cannot be negative")
        }

        @Test
        fun `should subtract specific resource type and quantity`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            val result = resources.minus(ResourceType.GOLD, Quantity(30))

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(70))
        }
    }

    @Nested
    inner class Multiplication {

        @Test
        fun `should multiply by integer`() {
            val resources = Resources.of(ResourceType.GOLD to 100, ResourceType.WOOD to 10)

            val result = resources * 3

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(300))
            assertThat(result.amountOf(ResourceType.WOOD)).isEqualTo(Quantity(30))
        }

        @Test
        fun `should multiply by zero resulting in empty`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            val result = resources * 0

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity.zero())
        }

        @Test
        fun `should multiply by one without change`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            val result = resources * 1

            assertThat(result).isEqualTo(resources)
        }

        @Test
        fun `should multiply by Quantity`() {
            val resources = Resources.of(ResourceType.GOLD to 100, ResourceType.GEMS to 2)

            val result = resources * Quantity(5)

            assertThat(result.amountOf(ResourceType.GOLD)).isEqualTo(Quantity(500))
            assertThat(result.amountOf(ResourceType.GEMS)).isEqualTo(Quantity(10))
        }
    }

    @Nested
    inner class Contains {

        @Test
        fun `should return true when containing all required resources`() {
            val available = Resources.of(ResourceType.GOLD to 100, ResourceType.WOOD to 50)
            val required = Resources.of(ResourceType.GOLD to 50, ResourceType.WOOD to 25)

            assertThat(required in available).isTrue()
        }

        @Test
        fun `should return true when containing exact amounts`() {
            val available = Resources.of(ResourceType.GOLD to 100)
            val required = Resources.of(ResourceType.GOLD to 100)

            assertThat(required in available).isTrue()
        }

        @Test
        fun `should return false when not containing enough of one resource`() {
            val available = Resources.of(ResourceType.GOLD to 100, ResourceType.WOOD to 10)
            val required = Resources.of(ResourceType.GOLD to 50, ResourceType.WOOD to 25)

            assertThat(required in available).isFalse()
        }

        @Test
        fun `should return true when empty resources are required`() {
            val available = Resources.of(ResourceType.GOLD to 100)
            val required = Resources.empty()

            assertThat(required in available).isTrue()
        }

        @Test
        fun `should return true when available is empty and required is empty`() {
            val available = Resources.empty()
            val required = Resources.empty()

            assertThat(required in available).isTrue()
        }
    }

    @Nested
    inner class Equality {

        @Test
        fun `should be equal when same resources`() {
            val resources1 = Resources.of(ResourceType.GOLD to 100, ResourceType.WOOD to 50)
            val resources2 = Resources.of(ResourceType.GOLD to 100, ResourceType.WOOD to 50)

            assertThat(resources1).isEqualTo(resources2)
            assertThat(resources1.hashCode()).isEqualTo(resources2.hashCode())
        }

        @Test
        fun `should not be equal when different amounts`() {
            val resources1 = Resources.of(ResourceType.GOLD to 100)
            val resources2 = Resources.of(ResourceType.GOLD to 200)

            assertThat(resources1).isNotEqualTo(resources2)
        }

        @Test
        fun `should not be equal when different types`() {
            val resources1 = Resources.of(ResourceType.GOLD to 100)
            val resources2 = Resources.of(ResourceType.WOOD to 100)

            assertThat(resources1).isNotEqualTo(resources2)
        }

        @Test
        fun `empty resources should be equal`() {
            val resources1 = Resources.empty()
            val resources2 = Resources.empty()

            assertThat(resources1).isEqualTo(resources2)
        }
    }

    @Nested
    inner class ToString {

        @Test
        fun `should return readable string representation`() {
            val resources = Resources.of(ResourceType.GOLD to 100)

            val result = resources.toString()

            assertThat(result).contains("Resources")
            assertThat(result).contains("GOLD")
        }
    }
}
