package com.dddheroes.heroesofddd

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<HeroesOfDDDApplication>().with(TestcontainersConfiguration::class).run(*args)
}
