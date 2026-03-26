package com.dddheroes.heroesofddd.maintenance.write.reprocessdeadletters

import com.dddheroes.sdk.restapi.ErrorResponse
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.TimeUnit

@ConditionalOnProperty(name = ["maintenance.enabled"], havingValue = "true")
@RestController
@RequestMapping("/maintenance/event-processors")
internal class ReprocessDeadLettersRestApi(
    private val axonConfiguration: AxonConfiguration
) {

    /**
     * Triggers dead letter reprocessing for a specific event handling component.
     *
     * The DLQ is scoped per component within a processor. The full processing group name
     * follows the pattern: `DeadLetterQueue[EventProcessor[processorName]][componentName]`.
     *
     * [limit] controls how many sequences to process (default: 1, use 0 for all).
     *
     * Examples:
     * - Retry 1 (default): `POST .../dead-letters/retries`
     * - Retry 10:           `POST .../dead-letters/retries?limit=10`
     * - Retry all:          `POST .../dead-letters/retries?limit=0`
     */
    @PostMapping("/{processorName}/components/{componentName}/dead-letters/retries")
    fun reprocessDeadLetters(
        @PathVariable processorName: String,
        @PathVariable componentName: String,
        @RequestParam(defaultValue = "1") limit: Int
    ): ResponseEntity<*> {
        val dlProcessor = when (val result = resolveDlProcessor(processorName, componentName)) {
            is DlProcessorResult.Found -> result.processor
            is DlProcessorResult.ProcessorNotFound -> return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("Event processor '$processorName' not found"))

            is DlProcessorResult.ComponentNotFound -> return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("Dead letter processor for component '$componentName' not found in processor '$processorName'. Is DLQ enabled for this processor?"))
        }

        val processedCount = processUpToLimit(dlProcessor, limit)

        return ResponseEntity.ok(
            ReprocessDeadLettersResponse(
                processor = processorName,
                component = componentName,
                processingGroup = dlqProcessingGroup(processorName, componentName),
                sequencesProcessed = processedCount
            )
        )
    }

    private sealed interface DlProcessorResult {
        data class Found(val processor: SequencedDeadLetterProcessor<*>) : DlProcessorResult
        data class ProcessorNotFound(val processorName: String) : DlProcessorResult
        data class ComponentNotFound(val processorName: String, val componentName: String) : DlProcessorResult
    }

    private fun resolveDlProcessor(processorName: String, componentName: String): DlProcessorResult {
        val moduleName = "EventProcessor[$processorName]"
        val moduleConfig = axonConfiguration.getModuleConfiguration(moduleName).orElse(null)
            ?: return DlProcessorResult.ProcessorNotFound(processorName)

        val componentInModuleName = "EventHandlingComponent[$moduleName][$componentName]"
        val dlProcessor = moduleConfig.getOptionalComponent(
            SequencedDeadLetterProcessor::class.java,
            componentInModuleName
        ).orElse(null) ?: return DlProcessorResult.ComponentNotFound(processorName, componentName)

        return DlProcessorResult.Found(dlProcessor)
    }

    private fun processUpToLimit(
        dlProcessor: SequencedDeadLetterProcessor<*>,
        limit: Int
    ): Int {
        val maxIterations = if (limit == 0) Int.MAX_VALUE else limit
        var processed = 0
        repeat(maxIterations) {
            val success = dlProcessor.processAny()
                .orTimeout(30, TimeUnit.SECONDS)
                .join()
            if (success) processed++ else return processed
        }
        return processed
    }

    private fun dlqProcessingGroup(processorName: String, componentName: String) =
        "DeadLetterQueue[EventProcessor[$processorName]][$componentName]"

    data class ReprocessDeadLettersResponse(
        val processor: String,
        val component: String,
        val processingGroup: String,
        val sequencesProcessed: Int
    )
}
