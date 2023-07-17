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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.net.InetAddress.getLocalHost;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.common.Nullable;
import com.google.inject.ProvisionException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import wiremock.com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class MyUrlProviderTest {

  private static final String HTTPD = "httpd";
  private static final String HTTPS = "https://";
  private static final String LISTEN_URL = "listenUrl";

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Configuration configurationMock;

  private Config gerritServerConfig;

  @Before
  public void setUp() {
    gerritServerConfig = new Config();
  }

  private MyUrlProvider getMyUrlProvider() {
    return getMyUrlProvider(null);
  }

  private MyUrlProvider getMyUrlProvider(@Nullable String myUrlEnvVar) {
    return new MyUrlProvider(gerritServerConfig, configurationMock, myUrlEnvVar);
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrl() throws Exception {
    String hostName = getLocalHost().getHostName();

    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://foo:8080");
    assertThat(getMyUrlProvider().get()).isEqualTo(HTTPS + hostName + ":8080");

    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://foo");
    assertThat(getMyUrlProvider().get()).isEqualTo(HTTPS + hostName);

    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://foo/");
    assertThat(getMyUrlProvider().get()).isEqualTo(HTTPS + hostName);
  }

  @Test
  public void testGetJGroupsMyUrlFromEnvVariable() throws Exception {
    String hostName = "https://foo:8080";
    assertThat(getMyUrlProvider(hostName).get()).isEqualTo(hostName);
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenNoListenUrlSpecified() throws Exception {
    ProvisionException thrown = assertThrows(ProvisionException.class, () -> getMyUrlProvider());
    assertThat(thrown).hasMessageThat().contains("exactly 1 value configured; found 0");
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenMultipleListenUrlsSpecified() throws Exception {
    gerritServerConfig.setStringList(HTTPD, null, LISTEN_URL, Lists.newArrayList("a", "b"));
    ProvisionException thrown = assertThrows(ProvisionException.class, () -> getMyUrlProvider());
    assertThat(thrown).hasMessageThat().contains("exactly 1 value configured; found 2");
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenReverseProxyConfigured() throws Exception {
    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "proxy-https://foo");
    ProvisionException thrown = assertThrows(ProvisionException.class, () -> getMyUrlProvider());
    assertThat(thrown).hasMessageThat().contains("when configured as reverse-proxy");
  }

  @Test
  public void testGetJGroupsMyUrlFromListenUrlWhenWildcardConfigured() throws Exception {
    gerritServerConfig.setString(HTTPD, null, LISTEN_URL, "https://*");
    ProvisionException thrown = assertThrows(ProvisionException.class, () -> getMyUrlProvider());
    assertThat(thrown).hasMessageThat().contains("when configured with wildcard");
  }

  @Test
  public void testGetJGroupsMyUrlOverridesListenUrl() throws Exception {
    when(configurationMock.peerInfoJGroups().myUrl()).thenReturn("http://somehost");
    assertThat(getMyUrlProvider().get()).isEqualTo("http://somehost");
  }

  @Test
  public void testGetJGroupsMyUrlOverridesEnvVariable() throws Exception {
    when(configurationMock.peerInfoJGroups().myUrl()).thenReturn("http://somehost");
    assertThat(getMyUrlProvider("https://foo:8080").get()).isEqualTo("http://somehost");
  }
}
