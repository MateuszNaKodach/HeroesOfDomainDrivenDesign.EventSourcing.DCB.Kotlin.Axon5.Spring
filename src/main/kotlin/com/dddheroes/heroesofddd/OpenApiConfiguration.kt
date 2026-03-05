package com.dddheroes.heroesofddd

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun heroesOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Heroes of DDD API")
                .description("REST API for Heroes of Might & Magic III - DDD, Event Sourcing, Event Modeling")
                .version("1.0.0")
        )

}
