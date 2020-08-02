package com.rafael.twitter.streamer.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
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
              @Value("\${twitter.bearer-token}") val bearerToken: String,
              @Value("\${kafka.broker-list}") val brokerList: String,
              @Value("\${kafka.stream-topic}") val topicName: String) {

    init {
        val producerConfig = mapOf(
                "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                "bootstrap.servers" to brokerList)
        val producer = KafkaProducer<String, String>(producerConfig)

        WebClient.create(baseUrl)
                .get()
                .uri(streamFilterPath)
                .accept(APPLICATION_JSON)
                .header("Authorization", token().let { "${it.type} ${it.value}" })
                .retrieve()
                .bodyToFlux(String::class.java)
                .filter { it.trim().isNotEmpty() }
                .subscribe {
                    it
                            .apply { producer.send(ProducerRecord(topicName, this)) }
                            .apply { println("Published tweet content $this to topic $topicName") }
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
