package com.dddheroes.heroesofddd.maintenance.write.processdlq

import org.axonframework.common.configuration.Configuration
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull


@RestController
private class ProcessEventProcessorDlqRestApi(private val configuration: Configuration) {

    @PutMapping("/maintenance/event-processors/{processorName}/dlq-processing")
    fun putEventProcessorDlqProcessing(
        @PathVariable processorName: String,
    ): CompletableFuture<ResponseEntity<Any>> {
        val processorModule =
            configuration.getModuleConfiguration(processorName).getOrNull()
                ?: return CompletableFuture.completedFuture(ResponseEntity.notFound().build())

        val dlqProcessors = processorModule.getComponents(SequencedDeadLetterProcessor::class.java).values
        return CompletableFuture.allOf(*dlqProcessors.map { it.processAny() }.toTypedArray())
            .thenApply { ResponseEntity.ok().build() }
    }
}
