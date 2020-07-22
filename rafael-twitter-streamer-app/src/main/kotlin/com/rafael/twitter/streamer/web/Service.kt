package com.rafael.twitter.streamer.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ExchangeStrategies.builder
import org.springframework.web.reactive.function.client.WebClient

@Component
class Service(@Value("\${twitter.base-url}") val baseUrl: String,
              @Value("\${twitter.stream-filter-path}") val streamFilterPath: String,
              @Value("\${twitter.oauth-path}") val oauthPath: String,
              @Value("\${twitter.bearer-token}") val bearerToken: String) {

    init {
        WebClient.create(baseUrl)
                .get()
                .uri(streamFilterPath)
                .accept(APPLICATION_JSON)
                .header("Authorization", token().let { "${it.type} ${it.value}" })
                .retrieve()
                .bodyToFlux(String::class.java)
                .subscribe {
                    println("Tweet received: $it")
                }
    }

    private fun token() = WebClient
            .builder()
            .exchangeStrategies(builder()
                    .codecs { it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false))) }
                    .build())
            .baseUrl(baseUrl)
            .build()
            .post()
            .uri(oauthPath)
            .accept(APPLICATION_JSON)
            .header("Authorization", "Basic $bearerToken")
            .retrieve()
            .bodyToMono(TwitterToken::class.java)
            .block()!!
            .also {
                println("Retrieved Token: $it")
            }

    data class TwitterToken(@JsonProperty("token_type") val type: String,
                            @JsonProperty("access_token") val value: String)
}
