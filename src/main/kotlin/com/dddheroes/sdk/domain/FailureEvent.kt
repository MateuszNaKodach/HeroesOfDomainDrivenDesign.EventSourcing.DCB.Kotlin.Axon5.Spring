package com.dddheroes.sdk.domain

interface FailureEvent : DomainEvent {
    val reason: String
}