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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PluginDaemonTest;

import com.ericsson.gerrit.plugins.highavailability.cache.Contants;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

@NoHttpd
public class CacheEvictionIT extends PluginDaemonTest {

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(18888);

  @Test
  @GerritConfigs({
      @GerritConfig(name = "plugin.high-availability.url", value = "http://localhost:18888"),
      @GerritConfig(name = "plugin.high-availability.user", value = "admin")})
  public void flushAndSendPost() throws Exception {
    final String flushRequest =
        "/plugins/high-availability/cache/" + Contants.PROJECT_LIST;
    wireMockRule.addMockServiceRequestListener(new RequestListener() {
      @Override
      public void requestReceived(Request request, Response response) {
        if (request.getAbsoluteUrl().contains(flushRequest)) {
          synchronized (flushRequest) {
            flushRequest.notify();
          }
        }
      }
    });
    givenThat(post(urlEqualTo(flushRequest))
        .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    adminSshSession
        .exec("gerrit flush-caches --cache " + Contants.PROJECT_LIST);
    synchronized (flushRequest) {
      flushRequest.wait(TimeUnit.SECONDS.toMillis(5));
    }
    verify(postRequestedFor(urlEqualTo(flushRequest)));
  }
}
