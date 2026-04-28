package com.dddheroes.heroesofddd

import org.springframework.test.context.ActiveProfilesResolver

class EventStoreProfilesResolver : ActiveProfilesResolver {
    override fun resolve(testClass: Class<*>): Array<String> {
        val backend = System.getProperty("axon.test.eventstore", "axonserver")
        return arrayOf("test", "testcontainers", backend)
    }
}
