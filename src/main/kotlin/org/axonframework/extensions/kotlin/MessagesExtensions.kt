package org.axonframework.extensions.kotlin

import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.MessageType
import org.axonframework.messaging.MetaData

/**
 * Extension function that treats a Collection as a list of event payloads
 * and converts each payload into a GenericEventMessage with the provided metadata.
 *
 * @param metaData The metadata to attach to each event message
 * @return List of GenericEventMessage instances, one for each element in the collection
 */
fun <T> Collection<T>.asEventMessages(metaData: MetaData = MetaData.emptyInstance()): List<GenericEventMessage<T>> {
    return this.map { payload ->
        GenericEventMessage(
            MessageType(payload!!::class.java),
            payload,
            metaData
        )
    }
}

fun <T> T.asEventMessage(metaData: MetaData = MetaData.emptyInstance()): GenericEventMessage<T> {
    return GenericEventMessage(
        MessageType(this!!::class.java),
        this,
        metaData
    )
}

fun <T> T.asCommandMessage(metaData: MetaData = MetaData.emptyInstance()): GenericEventMessage<T> {
    return GenericEventMessage(
        MessageType(this!!::class.java),
        this,
        metaData
    )
}

