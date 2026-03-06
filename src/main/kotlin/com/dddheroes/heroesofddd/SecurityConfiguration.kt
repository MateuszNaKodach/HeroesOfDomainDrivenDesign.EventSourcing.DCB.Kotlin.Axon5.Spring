package com.dddheroes.heroesofddd

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOriginPatterns: List<String> = listOf("*"),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"),
    val allowedHeaders: List<String> = listOf("*"),
    val allowCredentials: Boolean = true,
)

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfiguration(private val corsProperties: CorsProperties) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http
        .cors { it.configurationSource(corsConfigurationSource()) }
        .csrf { it.disable() }
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = corsProperties.allowedOriginPatterns
            allowedMethods = corsProperties.allowedMethods
            allowedHeaders = corsProperties.allowedHeaders
            allowCredentials = corsProperties.allowCredentials
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
