package org.snippetsearcher.snippet.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// el navegador bloquea los pedidos de la UI a este servicio si vienen de otro origen (localhost:5173 contra
// localhost:8080) y este no lo autoriza. Los origenes entran por variable de entorno, separados por coma.
// Con nginx adelante (consigna 5) UI y backend salen del mismo origen y esto deja de hacer falta
@Configuration
class CorsConfig(
    @Value("\${cors.allowed-origins}") allowedOrigins: String,
) : WebMvcConfigurer {
    private val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            // X-User-Id no es un header "simple": el navegador lo pregunta antes con un OPTIONS
            .allowedHeaders("*")
    }
}
