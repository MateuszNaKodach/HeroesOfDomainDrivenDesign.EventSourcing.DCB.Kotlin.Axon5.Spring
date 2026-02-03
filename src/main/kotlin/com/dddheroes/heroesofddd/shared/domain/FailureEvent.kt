package com.dddheroes.heroesofddd.shared.domain

interface FailureEvent : DomainEvent {
    val reason: String
}