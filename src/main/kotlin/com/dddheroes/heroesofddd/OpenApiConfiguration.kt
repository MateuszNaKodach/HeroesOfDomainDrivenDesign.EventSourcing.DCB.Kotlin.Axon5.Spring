package com.dddheroes.heroesofddd

import com.dddheroes.heroesofddd.shared.restapi.Headers
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.parameters.HeaderParameter
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.OperationCustomizer
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

    @Bean
    fun playerIdHeaderCustomizer(): OperationCustomizer = OperationCustomizer { operation, _ ->
        operation.addParametersItem(
            HeaderParameter()
                .name(Headers.PLAYER_ID)
                .description("Unique identifier of the player issuing the request")
                .required(true)
                .schema(StringSchema())
        )
        operation
    }

}
