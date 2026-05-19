// Copyright (C) 2026 The Android Open Source Project
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.time.Duration;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;

public class HttpClientProviderConnectionReuseTest {

  private static final String ENDPOINT = "/test";
  private static final Duration TIMEOUT = Duration.ofMillis(1000);
  private static final int CONNECTION_POOL_SIZE = 10;

  @Rule public WireMockRule wireMock = new WireMockRule(0);
  private Configuration cfg;
  private TestableHttpClientProvider provider;

  @Before
  public void setUp() {
    cfg = mock(Configuration.class, Answers.RETURNS_DEEP_STUBS);
    when(cfg.http().user()).thenReturn("");
    when(cfg.http().password()).thenReturn("");
    when(cfg.http().connectionTimeout()).thenReturn(TIMEOUT);
    when(cfg.http().socketTimeout()).thenReturn(TIMEOUT);
    when(cfg.http().connectionPoolSize()).thenReturn(CONNECTION_POOL_SIZE);

    provider = new TestableHttpClientProvider(cfg);
  }

  @Test
  public void connectionIsDiscardedAfter503() throws Exception {
    wireMock.givenThat(get(urlEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(503)));

    try (CloseableHttpClient client = provider.get()) {
      executeConsuming(client);
      // Connection must have been discarded (available == 0)
      assertThat(provider.connectionManager().getTotalStats().getAvailable()).isEqualTo(0);
    }
  }

  @Test
  public void connectionIsReturnedToPoolAfter500() throws Exception {
    wireMock.givenThat(get(urlEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(500)));

    try (CloseableHttpClient client = provider.get()) {
      executeConsuming(client);
      // 500 is not 503 — connection returned to the pool
      assertThat(provider.connectionManager().getTotalStats().getAvailable()).isEqualTo(1);
    }
  }

  private void executeConsuming(CloseableHttpClient client) throws Exception {
    String url = "http://localhost:" + wireMock.port() + ENDPOINT;
    try (CloseableHttpResponse response = client.execute(new HttpGet(url))) {
      if (response.getEntity() != null) {
        response.getEntity().getContent().close();
      }
    }
  }

  /**
   * Extends HttpClientProvider to capture the PoolingHttpClientConnectionManager so tests can
   * inspect pool statistics after requests complete.
   */
  private static class TestableHttpClientProvider extends HttpClientProvider {
    private PoolingHttpClientConnectionManager connManager;

    TestableHttpClientProvider(Configuration cfg) {
      super(cfg);
    }

    @Override
    PoolingHttpClientConnectionManager buildConnectionManager() {
      connManager = super.buildConnectionManager();
      return connManager;
    }

    PoolingHttpClientConnectionManager connectionManager() {
      return connManager;
    }
  }
}
