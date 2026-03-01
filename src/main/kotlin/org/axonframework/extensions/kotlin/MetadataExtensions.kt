package org.axonframework.extensions.kotlin

import org.axonframework.messaging.core.Message
import org.axonframework.messaging.core.Metadata

val Message.metadata: Metadata
    get() = this.metadata()

/**
 * Checks whether this [Metadata][AxonMetadata] contains all entries from the [other] metadata.
 *
 * Returns `true` if every key-value pair in [other] is present in this metadata
 * with the same value. An empty [other] metadata always returns `true`.
 *
 * @param other The metadata whose entries must all be present in this metadata
 * @return `true` if this metadata contains all key-value pairs from [other], `false` otherwise
 */
fun AxonMetadata.contains(other: AxonMetadata): Boolean {
    return other.entries.all { (key, value) -> this[key] == value }
}

typealias AxonMetadata = Metadata