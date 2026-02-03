package com.dddheroes.heroesofddd

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@Tag("integration")
@SpringBootTest
class HeroesOfDDDApplicationTests {

    @Test
    fun contextLoads() {
    }

}
