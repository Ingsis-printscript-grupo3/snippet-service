package org.snippetsearcher.snippet.common

import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.language.PRINTSCRIPT
import org.snippetsearcher.snippet.language.PrintScriptLanguageService
import org.snippetsearcher.snippet.permission.HttpPermissionClient
import org.snippetsearcher.snippet.permission.PermissionClient
import org.snippetsearcher.snippet.storage.AssetServiceContentStore
import org.snippetsearcher.snippet.storage.ContentStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

// arma un cliente por servicio externo. Las URLs salen de application.properties (variables de entorno)
@Configuration
class ClientsConfig(
    private val builder: RestClient.Builder,
) {
    // sumar un lenguaje nuevo es agregar una entrada en este mapa
    @Bean
    fun languageRegistry(
        @Value("\${services.printscript.url}") printScriptUrl: String,
    ): LanguageRegistry = LanguageRegistry(mapOf(PRINTSCRIPT to PrintScriptLanguageService(client(printScriptUrl))))

    @Bean
    fun permissionClient(
        @Value("\${services.permission.url}") url: String,
    ): PermissionClient = HttpPermissionClient(client(url))

    @Bean
    fun contentStore(
        @Value("\${services.asset.url}") url: String,
    ): ContentStore = AssetServiceContentStore(client(url))

    // clone: el builder es compartido y baseUrl lo modifica
    private fun client(baseUrl: String): RestClient = builder.clone().baseUrl(baseUrl).build()
}
