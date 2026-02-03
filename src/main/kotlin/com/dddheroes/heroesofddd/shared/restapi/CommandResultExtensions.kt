package com.dddheroes.heroesofddd.shared.restapi

import com.dddheroes.heroesofddd.shared.application.CommandHandlerResult
import org.springframework.http.ResponseEntity
import java.util.concurrent.CompletableFuture

fun <T : CommandHandlerResult> CompletableFuture<T>.toResponseEntity(): CompletableFuture<ResponseEntity<Any>> =
    thenApply {
        when (it) {
            is CommandHandlerResult.Success -> ResponseEntity.noContent().build()
            is CommandHandlerResult.Failure -> ResponseEntity.badRequest().body(ErrorResponse(it.message))
        }
    }