package com.rafael.twitter.streamer.web

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random.Default.nextLong

internal class ServiceTest {
    private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

    @BeforeEach
    fun setUp() {
        wireMockServer.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun test() {
        val values = listOf("tweet-${nextLong()}", "tweet-${nextLong()}", "tweet-${nextLong()}")

        wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_type\": \"$TOKEN_TYPE\", \"access_token\": \"$TOKEN_VALUE\"}"))) // TODO Convert this to JSON body

        wireMockServer.stubFor(get(urlMatching(STREAM_FILTER_PATH))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(values.joinToString(separator = "\n"))))

        Service(wireMockServer.baseUrl(), STREAM_FILTER_PATH, OAUTH_PATH, BEARER_TOKEN)

        wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

        await untilAsserted {
            wireMockServer.verify(getRequestedFor(urlMatching(STREAM_FILTER_PATH))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE")))
        }
    }

    companion object {
        val STREAM_FILTER_PATH = "/resource-path-${nextLong()}"
        val OAUTH_PATH = "/oauth-path-${nextLong()}"
        val TOKEN_TYPE = "token-type-${nextLong()}"
        val TOKEN_VALUE = "token-value-${nextLong()}"
        val BEARER_TOKEN = "bearer-token-${nextLong()}"
    }
}