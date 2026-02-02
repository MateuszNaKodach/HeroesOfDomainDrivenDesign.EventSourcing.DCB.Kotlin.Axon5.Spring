package org.axonframework.extensions.kotlin

import org.axonframework.messaging.eventhandling.GenericEventMessage
import org.axonframework.messaging.core.MessageType

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

typealias AxonMetadata = org.axonframework.messaging.core.Metadata

