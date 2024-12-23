// Copyright (C) 2018 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.index;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@Ignore
@NoHttpd
@TestPlugin(
    name = "high-availability",
    sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
    httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule")
public abstract class AbstractIndexForwardingIT extends LightweightPluginDaemonTest {

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

  @Inject SitePaths sitePaths;

  @Override
  public void setUpTestPlugin() throws Exception {
    givenThat(any(anyUrl()).willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
    FileBasedConfig fileBasedConfig =
        new FileBasedConfig(
            sitePaths.etc_dir.resolve(Configuration.PLUGIN_CONFIG_FILE).toFile(), FS.DETECTED);
    fileBasedConfig.setString("peerInfo", "static", "url", url());
    fileBasedConfig.setInt("http", null, "retryInterval", 100);
    fileBasedConfig.save();
    beforeAction();
    super.setUpTestPlugin();
  }

  @Test
  @UseLocalDisk
  public void testIndexForwarding() throws Exception {
    String expectedRequest = getExpectedRequest();
    CountDownLatch expectedRequestLatch = new CountDownLatch(1);
    wireMockRule.addMockServiceRequestListener(
        (request, response) -> {
          if (request.getAbsoluteUrl().contains(expectedRequest)) {
            expectedRequestLatch.countDown();
          }
        });
    givenThat(
        post(urlEqualTo(expectedRequest))
            .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
    doAction();
    assertThat(expectedRequestLatch.await(5, TimeUnit.SECONDS)).isTrue();
    verify(postRequestedFor(urlEqualTo(expectedRequest)));
  }

  private String url() {
    return "http://localhost:" + wireMockRule.port();
  }

  /** Perform pre-test setup. */
  protected abstract void beforeAction() throws Exception;

  /**
   * Get the URL on which a request is expected.
   *
   * @return the URL.
   */
  protected abstract String getExpectedRequest();

  /** Perform the action that should cause an index operation to occur. */
  protected abstract void doAction() throws Exception;
}
