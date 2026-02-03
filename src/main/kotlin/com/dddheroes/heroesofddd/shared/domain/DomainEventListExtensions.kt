package com.dddheroes.heroesofddd.shared.domain

inline fun <reified T : DomainEvent> Collection<T>.throwIfFailure(): Collection<T> {
    val failureEvents = this.filterIsInstance<FailureEvent>()
    if (failureEvents.isEmpty()) {
        return this
    }
    val message: String = failureEvents.map { it.reason }.joinToString { ", " }
    throw DomainRuleViolatedException(message)
}