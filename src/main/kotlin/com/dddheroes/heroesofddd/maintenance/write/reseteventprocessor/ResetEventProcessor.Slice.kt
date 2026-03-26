package com.dddheroes.heroesofddd.maintenance.write.reseteventprocessor

import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@ConditionalOnProperty(name = ["maintenance.enabled"], havingValue = "true")
@RestController
@RequestMapping("/maintenance/event-processors")
internal class ResetEventProcessorRestApi(
    private val axonConfiguration: AxonConfiguration
) {

    @PostMapping("/{processorName}/resets")
    fun resetProcessor(@PathVariable processorName: String): ResponseEntity<ResetResponse> {
        val moduleName = "EventProcessor[$processorName]"
        val moduleConfig = axonConfiguration.getModuleConfiguration(moduleName)
            .orElse(null) ?: return ResponseEntity.notFound().build()

        val processor = moduleConfig.getOptionalComponent(StreamingEventProcessor::class.java, moduleName)
            .orElse(null) ?: return ResponseEntity.notFound().build()

        processor.shutdown().join()
        processor.resetTokens().join()
        processor.start().join()

        return ResponseEntity.ok(ResetResponse(processorName, "reset completed"))
    }

    data class ResetResponse(val processor: String, val status: String)
}
