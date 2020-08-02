package com.rafael.twitter.streamer.web

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import net.mguenther.kafka.junit.EmbeddedKafkaCluster.provisionWith
import net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.useDefaults
import net.mguenther.kafka.junit.ReadKeyValues.from
import org.awaitility.kotlin.*
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json
import java.time.Duration.ofSeconds
import java.util.stream.Stream
import kotlin.random.Random.Default.nextLong

internal class ServiceTest {
    private val streamFilterPath = "/resource-path-${nextLong()}"
    private val oauthPath = "/oauth-path-${nextLong()}"
    private val tokenType = "token-type-${nextLong()}"
    private val tokenValue = "token-value-${nextLong()}"
    private val bearerToken = "bearer-token-${nextLong()}"
    private val topicName = "tweet-stream-${nextLong()}"
    private val jsonBuilder = json()
            .modulesToInstall(KotlinModule())
            .build<ObjectMapper>()
            .setSerializationInclusion(NON_NULL)

    private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
    private val kafkaCluster = provisionWith(useDefaults())

    @BeforeEach
    fun setUp() {
        wireMockServer.start()
        kafkaCluster.start()
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
        kafkaCluster.stop()
    }

    @TestInstance(PER_CLASS)
    @Nested
    inner class Streaming {

        @ParameterizedTest
        @MethodSource("streamingTestCases")
        fun shouldPublishNonEmptyTweetsFromStreamToKafka(tweets: List<String>,
                                                         expectedTweetsMatcher: Matcher<List<String>>,
                                                         description: String) {
            wireMockServer.stubFor(post(urlMatching(oauthPath))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $bearerToken"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(tokenType, tokenValue)))))

            wireMockServer.stubFor(get(urlMatching(streamFilterPath))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$tokenType $tokenValue"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(tweets.joinToString(separator = "\n"))))

            Service(wireMockServer.baseUrl(), streamFilterPath, oauthPath, bearerToken, kafkaCluster.brokerList, topicName)

            wireMockServer.verify(postRequestedFor(urlMatching(oauthPath))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $bearerToken")))

            await atMost ofSeconds(10) atLeast ofSeconds(1) withPollInterval ofSeconds(1) untilAsserted {
                wireMockServer.verify(getRequestedFor(urlMatching(streamFilterPath))
                        .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                        .withHeader("Authorization", equalTo("$tokenType $tokenValue")))

                assertThat(kafkaCluster.readValues(from(topicName).useDefaults()), expectedTweetsMatcher)
            }
        }

        private fun streamingTestCases() = Stream.of(
                Arguments.of(
                        listOf("tweet-1"),
                        contains("tweet-1"),
                        "Single non-empty tweet"),
                Arguments.of(
                        listOf("tweet-1", "tweet-2", "tweet-3"),
                        contains("tweet-1", "tweet-2", "tweet-3"),
                        "Multiple non-empty tweets"),
                Arguments.of(
                        listOf(""),
                        empty<String>(),
                        "Empty tweet"),
                Arguments.of(
                        listOf("\t\t"),
                        empty<String>(),
                        "Tab tweet"),
                Arguments.of(
                        listOf("\r"),
                        empty<String>(),
                        "Line feed tweet"),
                Arguments.of(
                        listOf("             "),
                        empty<String>(),
                        "Space tweet"),
                Arguments.of(
                        listOf("   \t   \r     \t\t \t"),
                        empty<String>(),
                        "Single empty tweet with various white space characters"),
                Arguments.of(
                        listOf("\r  ", " ", "\t\t\r", "  \t   "),
                        empty<String>(),
                        "Multiple empty tweets"),
                Arguments.of(
                        listOf("  This\t tweet   contains  \twhitespace characters\t  "),
                        contains("  This\t tweet   contains  \twhitespace characters\t  "),
                        "Single non-empty tweet with whitespace characters"),
                Arguments.of(
                        listOf("\ttweet-1  ", "    ", "TWEET-2 \t", " TwEeT-3 "),
                        contains("\ttweet-1  ", "TWEET-2 \t", " TwEeT-3 "),
                        "Mix of empty and non-empty tweets"))
    }

    data class TwitterGetTokenResponse(@JsonProperty("token_type") val type: String,
                                       @JsonProperty("access_token") val value: String)
}