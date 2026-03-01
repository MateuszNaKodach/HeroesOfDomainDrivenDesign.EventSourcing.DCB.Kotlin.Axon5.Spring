package org.axonframework.extensions.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MetadataExtensionsTest {

    @Nested
    inner class `Metadata contains another Metadata` {

        @Test
        fun `returns true when both are empty`() {
            val metadata = AxonMetadata.emptyInstance()
            val other = AxonMetadata.emptyInstance()

            assertThat(metadata.contains(other)).isTrue()
        }

        @Test
        fun `returns true when other is empty`() {
            val metadata = AxonMetadata.with("key", "value")
            val other = AxonMetadata.emptyInstance()

            assertThat(metadata.contains(other)).isTrue()
        }

        @Test
        fun `returns true when both have same single entry`() {
            val metadata = AxonMetadata.with("gameId", "game-1")
            val other = AxonMetadata.with("gameId", "game-1")

            assertThat(metadata.contains(other)).isTrue()
        }

        @Test
        fun `returns true when metadata is a superset`() {
            val metadata = AxonMetadata.with("gameId", "game-1")
                .and("playerId", "player-1")
                .and("extra", "data")
            val other = AxonMetadata.with("gameId", "game-1")
                .and("playerId", "player-1")

            assertThat(metadata.contains(other)).isTrue()
        }

        @Test
        fun `returns false when key is missing`() {
            val metadata = AxonMetadata.with("gameId", "game-1")
            val other = AxonMetadata.with("gameId", "game-1")
                .and("playerId", "player-1")

            assertThat(metadata.contains(other)).isFalse()
        }

        @Test
        fun `returns false when value differs`() {
            val metadata = AxonMetadata.with("gameId", "game-1")
            val other = AxonMetadata.with("gameId", "game-2")

            assertThat(metadata.contains(other)).isFalse()
        }

        @Test
        fun `returns false when this is empty but other is not`() {
            val metadata = AxonMetadata.emptyInstance()
            val other = AxonMetadata.with("gameId", "game-1")

            assertThat(metadata.contains(other)).isFalse()
        }

        @Test
        fun `returns true with multiple matching entries among extras`() {
            val metadata = AxonMetadata.with("a", "1")
                .and("b", "2")
                .and("c", "3")
                .and("d", "4")
            val other = AxonMetadata.with("b", "2")
                .and("d", "4")

            assertThat(metadata.contains(other)).isTrue()
        }
    }
}
