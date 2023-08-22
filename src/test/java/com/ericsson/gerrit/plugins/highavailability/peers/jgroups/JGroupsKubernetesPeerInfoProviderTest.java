// Copyright (C) 2023 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import com.google.gerrit.extensions.restapi.Url;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(MockitoJUnitRunner.class)
public class JGroupsKubernetesPeerInfoProviderTest {

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Configuration pluginConfigurationMock;

  private InetAddressFinder finder;
  private JGroupsPeerInfoProvider jGroupsPeerInfoProvider;
  @Mock private MyUrlProvider myUrlProviderTest;

  @Rule public WireMockRule kubeApiMock = new WireMockRule(options().port(48443));

  @Before
  public void setUp() throws Exception {
    finder = new InetAddressFinder(pluginConfigurationMock);
    jGroupsPeerInfoProvider =
        new JGroupsPeerInfoProvider(pluginConfigurationMock, finder, myUrlProviderTest);
  }

  @Test
  public void testPodListIsRequested() throws Exception {
    String namespace = "gerrit";
    List<String> labels = List.of("app=gerrit", "mode=primary");
    when(pluginConfigurationMock.jgroups().useKubernetes()).thenReturn(true);
    when(pluginConfigurationMock.jgroups().clusterName()).thenReturn("gerritCluster");
    when(pluginConfigurationMock.jgroupsKubernetes().namespace()).thenReturn(namespace);
    when(pluginConfigurationMock.jgroupsKubernetes().labels()).thenReturn(labels);

    StringBuilder kubeApiUrlBuilder = new StringBuilder();
    kubeApiUrlBuilder.append("/api/v1/namespaces/");
    kubeApiUrlBuilder.append(namespace);
    kubeApiUrlBuilder.append("/pods?labelSelector=");
    kubeApiUrlBuilder.append(Url.encode(String.join(",", labels)));
    String kubeApiUrl = kubeApiUrlBuilder.toString();

    String respJson = Resources.toString(this.getClass().getResource("pod-list.json"), UTF_8);

    kubeApiMock.stubFor(
        get(urlEqualTo(kubeApiUrl))
            .willReturn(
                aResponse()
                    .withJsonBody(new ObjectMapper().readTree(respJson))
                    .withStatus(HttpStatus.SC_OK)));

    System.setProperty("KUBERNETES_MASTER_PROTOCOL", "http");
    System.setProperty("KUBERNETES_SERVICE_HOST", "localhost");
    System.setProperty("KUBERNETES_SERVICE_PORT", "48443");
    System.setProperty("java.net.preferIPv4Stack", "true");

    jGroupsPeerInfoProvider.connect();
    verify(getRequestedFor(urlEqualTo(kubeApiUrl)));
  }
}
