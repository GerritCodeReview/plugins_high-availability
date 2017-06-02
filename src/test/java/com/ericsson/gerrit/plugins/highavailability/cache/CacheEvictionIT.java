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

package com.ericsson.gerrit.plugins.highavailability.cache;

import static com.ericsson.gerrit.plugins.highavailability.Configuration.CACHE_THREAD_POOL_SIZE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SHARED_DIRECTORY_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.URL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.USER_KEY;
import static com.google.common.truth.Truth.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Throwables;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PluginDaemonTest;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

@NoHttpd
public class CacheEvictionIT extends PluginDaemonTest {
  private static final String CACHE_SIZE = "10";
  private static final String SHARED_DIRECTORY = "/some/directory";
  private static final String URL = "http://localhost:18888";
  private static final String USER = "admin";

  @Mock private PluginConfigFactory cfgFactoryMock;
  @Mock private PluginConfig configMock;

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(18888), false);

  @Before
  public void setUp() {
    initMocks(this);
    assertThat(configMock).isNotNull();
    assertThat(cfgFactoryMock).isNotNull();
    when(cfgFactoryMock.getFromGerritConfig(pluginName, true)).thenReturn(configMock);
    when(configMock.getString(CACHE_THREAD_POOL_SIZE_KEY)).thenReturn(CACHE_SIZE);
    when(configMock.getString(SHARED_DIRECTORY_KEY)).thenReturn(SHARED_DIRECTORY);
    when(configMock.getString(URL_KEY)).thenReturn(URL);
    when(configMock.getString(USER_KEY)).thenReturn(USER);
  }

  @Test
  public void flushAndSendPost() throws Exception {
    final String flushRequest = "/plugins/high-availability/cache/" + Constants.PROJECT_LIST;
    final CyclicBarrier checkPoint = new CyclicBarrier(2);
    wireMockRule.addMockServiceRequestListener(
        new RequestListener() {
          @Override
          public void requestReceived(Request request, Response response) {
            if (request.getAbsoluteUrl().contains(flushRequest)) {
              try {
                checkPoint.await();
              } catch (InterruptedException | BrokenBarrierException e) {
                Throwables.propagateIfPossible(e);
              }
            }
          }
        });
    givenThat(
        post(urlEqualTo(flushRequest))
            .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

    adminSshSession.exec("gerrit flush-caches --cache " + Constants.PROJECT_LIST);
    checkPoint.await(5, TimeUnit.SECONDS);
    verify(postRequestedFor(urlEqualTo(flushRequest)));
  }
}
