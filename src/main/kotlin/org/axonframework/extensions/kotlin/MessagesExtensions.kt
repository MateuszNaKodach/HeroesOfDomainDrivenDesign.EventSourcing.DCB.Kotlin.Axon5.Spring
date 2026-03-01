package org.axonframework.extensions.kotlin

import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.eventhandling.GenericEventMessage

/**
 * Extension function that treats a Collection as a list of event payloads
 * and converts each payload into a GenericEventMessage with the provided metadata.
 *
 * @param metadata The metadata to attach to each event message
 * @return List of GenericEventMessage instances, one for each element in the collection
 */
fun <T> Collection<T>.asEventMessages(metadata: AxonMetadata = AxonMetadata.emptyInstance()): List<GenericEventMessage> {
    return this.map { payload ->
        GenericEventMessage(
            MessageType(payload!!::class.java),
            payload,
            metadata
        )
    }
}

fun <T> T.asEventMessage(metadata: AxonMetadata = AxonMetadata.emptyInstance()): GenericEventMessage {
    return GenericEventMessage(
        MessageType(this!!::class.java),
        this,
        metadata
    )
}

fun <T> T.asCommandMessage(metadata: AxonMetadata = AxonMetadata.emptyInstance()): GenericEventMessage {
    return GenericEventMessage(
        MessageType(this!!::class.java),
        this,
        metadata
    )
}

val org.axonframework.messaging.core.Message.metadata: org.axonframework.messaging.core.Metadata
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

typealias AxonMetadata = org.axonframework.messaging.core.Metadata