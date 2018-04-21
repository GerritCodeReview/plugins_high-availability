// Copyright (C) 2015 Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;

public class HttpSessionTest {
  private static final int MAX_TRIES = 3;
  private static final int RETRY_INTERVAL = 250;
  private static final int TIMEOUT = 500;
  private static final int ERROR = 500;
  private static final int NO_CONTENT = 204;
  private static final int NOT_FOUND = 404;
  private static final int UNAUTHORIZED = 401;

  private static final String ENDPOINT = "/plugins/high-availability/index/1";
  private static final String BODY = "SerializedEvent";
  private static final String ERROR_MESSAGE = "Error message";
  private static final String REQUEST_MADE = "Request made";
  private static final String SECOND_TRY = "Second try";
  private static final String THIRD_TRY = "Third try";
  private static final String RETRY_AT_DELAY = "Retry at delay";

  private HttpSession httpSession;

  @Rule public WireMockRule wireMockRule = new WireMockRule(0);

  private Configuration configMock;

  @Before
  public void setUp() throws Exception {
    String url = "http://localhost:" + wireMockRule.port();
    configMock = mock(Configuration.class, Answers.RETURNS_DEEP_STUBS);
    when(configMock.http().user()).thenReturn("user");
    when(configMock.http().password()).thenReturn("pass");
    when(configMock.main().maxTries()).thenReturn(MAX_TRIES);
    when(configMock.http().connectionTimeout()).thenReturn(TIMEOUT);
    when(configMock.http().socketTimeout()).thenReturn(TIMEOUT);
    when(configMock.main().retryInterval()).thenReturn(RETRY_INTERVAL);

    PeerInfo peerInfo = mock(PeerInfo.class);
    when(peerInfo.getDirectUrl()).thenReturn(url);
    httpSession =
        new HttpSession(
            new HttpClientProvider(configMock).get(), Providers.of(Optional.of(peerInfo)));
  }

  @Test
  public void testPostResponseOK() throws Exception {
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(NO_CONTENT)));

    assertThat(httpSession.post(ENDPOINT).isSuccessful()).isTrue();
  }

  @Test
  public void testPostResponseWithContentOK() throws Exception {
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .withRequestBody(equalTo(BODY))
            .willReturn(aResponse().withStatus(NO_CONTENT)));
    assertThat(httpSession.post(ENDPOINT, BODY).isSuccessful()).isTrue();
  }

  @Test
  public void testDeleteResponseOK() throws Exception {
    wireMockRule.givenThat(
        delete(urlEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(NO_CONTENT)));

    assertThat(httpSession.delete(ENDPOINT).isSuccessful()).isTrue();
  }

  @Test
  public void testNotAuthorized() throws Exception {
    String expected = "unauthorized";
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .willReturn(aResponse().withStatus(UNAUTHORIZED).withBody(expected)));

    HttpResult result = httpSession.post(ENDPOINT);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getMessage()).isEqualTo(expected);
  }

  @Test
  public void testNotFound() throws Exception {
    String expected = "not found";
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .willReturn(aResponse().withStatus(NOT_FOUND).withBody(expected)));

    HttpResult result = httpSession.post(ENDPOINT);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getMessage()).isEqualTo(expected);
  }

  @Test
  public void testBadResponseRetryThenGiveUp() throws Exception {
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .willReturn(aResponse().withStatus(ERROR).withBody(ERROR_MESSAGE)));

    HttpResult result = httpSession.post(ENDPOINT);
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getMessage()).isEqualTo(ERROR_MESSAGE);
  }

  @Test(expected = SocketTimeoutException.class)
  public void testMaxRetriesAfterTimeoutThenGiveUp() throws Exception {
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .inScenario(RETRY_AT_DELAY)
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo(REQUEST_MADE)
            .willReturn(aResponse().withFixedDelay(TIMEOUT)));
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .inScenario(RETRY_AT_DELAY)
            .whenScenarioStateIs(REQUEST_MADE)
            .willSetStateTo(SECOND_TRY)
            .willReturn(aResponse().withFixedDelay(TIMEOUT)));
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .inScenario(RETRY_AT_DELAY)
            .whenScenarioStateIs(SECOND_TRY)
            .willSetStateTo(THIRD_TRY)
            .willReturn(aResponse().withFixedDelay(TIMEOUT)));
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .inScenario(RETRY_AT_DELAY)
            .whenScenarioStateIs(THIRD_TRY)
            .willReturn(aResponse().withFixedDelay(TIMEOUT)));

    httpSession.post(ENDPOINT);
  }

  @Test
  public void testResponseWithMalformedResponse() throws Exception {
    wireMockRule.givenThat(
        post(urlEqualTo(ENDPOINT))
            .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThat(httpSession.post(ENDPOINT).isSuccessful()).isFalse();
  }

  @Test
  public void testNoRequestWhenPeerInfoUnknown() throws IOException {
    httpSession =
        new HttpSession(new HttpClientProvider(configMock).get(), Providers.of(Optional.empty()));
    try {
      httpSession.post(ENDPOINT);
      fail("Expected PeerInfoNotAvailableException");
    } catch (PeerInfoNotAvailableException e) {
      // good
    }
    verify(exactly(0), anyRequestedFor(anyUrl()));
  }
}
