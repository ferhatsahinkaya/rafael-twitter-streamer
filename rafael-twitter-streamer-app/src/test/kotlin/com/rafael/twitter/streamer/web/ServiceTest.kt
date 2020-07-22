package com.rafael.twitter.streamer.web

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json
import kotlin.random.Random.Default.nextLong

internal class ServiceTest {
    private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
    private val jsonBuilder = json()
            .modulesToInstall(KotlinModule())
            .build<ObjectMapper>()
            .setSerializationInclusion(NON_NULL)

    @BeforeEach
    fun setUp() {
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun shouldCallStreamFilterPath() {
        val values = listOf("tweet-${nextLong()}", "tweet-${nextLong()}", "tweet-${nextLong()}")

        wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                        .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

        wireMockServer.stubFor(get(urlMatching(STREAM_FILTER_PATH))
                .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                        .withBody(values.joinToString(separator = "\n"))))

        Service(wireMockServer.baseUrl(), STREAM_FILTER_PATH, OAUTH_PATH, BEARER_TOKEN)

        wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

        await untilAsserted {
            wireMockServer.verify(getRequestedFor(urlMatching(STREAM_FILTER_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE")))
        }
    }

    data class TwitterGetTokenResponse(@JsonProperty("token_type") val type: String,
                                       @JsonProperty("access_token") val value: String)

    companion object {
        private val STREAM_FILTER_PATH = "/resource-path-${nextLong()}"
        private val OAUTH_PATH = "/oauth-path-${nextLong()}"
        private val TOKEN_TYPE = "token-type-${nextLong()}"
        private val TOKEN_VALUE = "token-value-${nextLong()}"
        private val BEARER_TOKEN = "bearer-token-${nextLong()}"
    }
}