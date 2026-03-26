package com.dddheroes.heroesofddd.maintenance.write.reprocessdeadletters

import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor
import org.axonframework.messaging.eventhandling.EventMessage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Triggers dead letter reprocessing for a specific event handling component.
 *
 * The DLQ is scoped per component within a processor. The full processing group name
 * follows the pattern: `DeadLetterQueue[EventProcessor[processorName]][componentName]`.
 *
 * Example: `POST /maintenance/event-processors/ReadModel_GetAllDwellings/components/dwellingReadModelProjector/dead-letters/process`
 */
@ConditionalOnProperty(name = ["maintenance.enabled"], havingValue = "true")
@RestController
@RequestMapping("/maintenance/event-processors")
internal class ReprocessDeadLettersRestApi(
    private val axonConfiguration: AxonConfiguration
) {

    /**
     * Reprocess dead letters for a specific component within a processor.
     * Each invocation processes a single dead letter sequence.
     * Returns true if a dead letter was successfully reprocessed, false if none were available.
     */
    @PostMapping("/{processorName}/components/{componentName}/dead-letters/process")
    fun reprocessDeadLetters(
        @PathVariable processorName: String,
        @PathVariable componentName: String
    ): ResponseEntity<ReprocessDeadLettersResponse> {
        val moduleName = "EventProcessor[$processorName]"
        val moduleConfig = axonConfiguration.getModuleConfiguration(moduleName)
            .orElse(null) ?: return ResponseEntity.notFound().build()

        @Suppress("UNCHECKED_CAST")
        val dlProcessor = moduleConfig.getOptionalComponent(
            SequencedDeadLetterProcessor::class.java as Class<SequencedDeadLetterProcessor<EventMessage>>,
            componentName
        ).orElse(null) ?: return ResponseEntity.notFound().build()

        val processed = dlProcessor.processAny().join()

        return ResponseEntity.ok(
            ReprocessDeadLettersResponse(
                processor = processorName,
                component = componentName,
                processingGroup = "DeadLetterQueue[$moduleName][$componentName]",
                processed = processed
            )
        )
    }

    data class ReprocessDeadLettersResponse(
        val processor: String,
        val component: String,
        val processingGroup: String,
        val processed: Boolean
    )
}
