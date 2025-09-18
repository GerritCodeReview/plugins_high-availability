// Copyright (C) 2015 The Android Open Source Project
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

import static com.ericsson.gerrit.plugins.highavailability.forwarder.rest.RestForwarder.buildAllChangesForProjectEndpoint;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwarderMetrics;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwarderMetricsRegistry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.TestEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.inject.Provider;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class RestForwarderTest {
  private static final String URL = "http://fake.com";
  private static final String PLUGIN_NAME = "high-availability";
  private static final String EMPTY_MSG = "";
  private static final String ERROR = "Error";
  private static final String PLUGINS = "plugins";
  private static final String PROJECT_TO_ADD = "projectToAdd";
  private static final String PROJECT_TO_DELETE = "projectToDelete";
  private static final String SUCCESS = "Success";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;

  // Index
  private static final int CHANGE_NUMBER = 1;
  private static final String PROJECT_NAME = "test/project";
  private static final String PROJECT_NAME_URL_END = "test%2Fproject";
  private static final String INDEX_CHANGE_ENDPOINT =
      Joiner.on("/")
          .join(
              URL,
              PLUGINS,
              PLUGIN_NAME,
              "index/change",
              PROJECT_NAME_URL_END + "~" + CHANGE_NUMBER);
  private static final String INDEX_BATCH_CHANGE_ENDPOINT =
      Joiner.on("/")
          .join(
              URL,
              PLUGINS,
              PLUGIN_NAME,
              "index/change/batch",
              PROJECT_NAME_URL_END + "~" + CHANGE_NUMBER);
  private static final String DELETE_CHANGE_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "index/change", "~" + CHANGE_NUMBER);
  private static final String DELETE_ALL_CHANGES_ENDPOINT =
      Joiner.on("/")
          .join(
              URL,
              PLUGINS,
              PLUGIN_NAME,
              "index/change",
              buildAllChangesForProjectEndpoint(PROJECT_NAME));
  private static final int ACCOUNT_NUMBER = 2;
  private static final String INDEX_ACCOUNT_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "index/account", ACCOUNT_NUMBER);
  private static final String UUID = "we235jdf92nfj2351";
  private static final String INDEX_GROUP_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "index/group", UUID);

  // Event
  private static Event event = new TestEvent();
  private static final String EVENT_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "event", event.type);

  private static final long TEST_TIMEOUT = 10;
  private static final TimeUnit TEST_TIMEOUT_UNITS = TimeUnit.SECONDS;

  private RestForwarder forwarder;
  private HttpSession httpSessionMock;
  private Gson gson = new Gson();
  private Configuration configMock;
  Provider<Set<PeerInfo>> peersMock;

  @Mock ForwarderMetricsRegistry metricsRegistry;
  @Mock ForwarderMetrics metrics;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    httpSessionMock = mock(HttpSession.class);
    configMock = mock(Configuration.class, Answers.RETURNS_DEEP_STUBS);
    when(configMock.http().maxTries()).thenReturn(3);
    when(configMock.http().retryInterval()).thenReturn(Duration.ofMillis(10));
    when(configMock.http().threadPoolSize()).thenReturn(2);
    peersMock = mock(Provider.class);
    when(peersMock.get()).thenReturn(ImmutableSet.of(new PeerInfo(URL)));
    when(metricsRegistry.get(any())).thenReturn(metrics);
    forwarder =
        new RestForwarder(
            httpSessionMock,
            PLUGIN_NAME,
            configMock,
            peersMock,
            gson, // TODO: Create provider
            new FailsafeExecutorProvider(configMock).get(),
            metricsRegistry);
  }

  @Test
  public void testIndexAccountOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_ACCOUNT_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .indexAccount(ACCOUNT_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testIndexAccountFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_ACCOUNT_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .indexAccount(ACCOUNT_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testIndexAccountThrowsException() throws Exception {
    when(httpSessionMock.post(eq(INDEX_ACCOUNT_ENDPOINT), any(), any()))
        .thenThrow(IOException.class);
    assertThat(
            forwarder
                .indexAccount(ACCOUNT_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testIndexGroupOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_GROUP_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .indexGroup(UUID, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testIndexGroupFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_GROUP_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .indexGroup(UUID, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testIndexGroupThrowsException() throws Exception {
    when(httpSessionMock.post(eq(INDEX_GROUP_ENDPOINT), any(), any())).thenThrow(IOException.class);
    assertThat(
            forwarder
                .indexGroup(UUID, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testIndexChangeOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_CHANGE_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .indexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testIndexChangeFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_CHANGE_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .indexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testIndexChangeThrowsException() throws Exception {
    when(httpSessionMock.post(eq(INDEX_CHANGE_ENDPOINT), any(), any()))
        .thenThrow(IOException.class);
    assertThat(
            forwarder
                .indexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testIndexBatchChangeOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_BATCH_CHANGE_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .batchIndexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())
                .get()
                .getResult())
        .isTrue();
  }

  @Test
  public void testIndexBatchChangeFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_BATCH_CHANGE_ENDPOINT), any(), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .batchIndexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())
                .get()
                .getResult())
        .isFalse();
  }

  @Test
  public void testIndexBatchChangeThrowsException() throws Exception {
    when(httpSessionMock.post(eq(INDEX_BATCH_CHANGE_ENDPOINT), any(), any()))
        .thenThrow(IOException.class);
    assertThat(
            forwarder
                .batchIndexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())
                .get()
                .getResult())
        .isFalse();
  }

  @Test
  public void testChangeDeletedFromIndexOK() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .deleteChangeFromIndex(CHANGE_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testAllChangesDeletedFromIndexOK() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_ALL_CHANGES_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .deleteAllChangesForProject(Project.nameKey(PROJECT_NAME))
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testChangeDeletedFromIndexFailed() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .deleteChangeFromIndex(CHANGE_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testChangeDeletedFromThrowsException() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_CHANGE_ENDPOINT), any())).thenThrow(IOException.class);
    assertThat(
            forwarder
                .deleteChangeFromIndex(CHANGE_NUMBER, new IndexEvent())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testEventSentOK() throws Exception {
    when(httpSessionMock.post(eq(EVENT_ENDPOINT), eq(event), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.send(event).get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS).getResult()).isTrue();
  }

  @Test
  public void testEventSentFailed() throws Exception {
    when(httpSessionMock.post(eq(EVENT_ENDPOINT), eq(event), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.send(event).get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS).getResult()).isFalse();
  }

  @Test
  public void testEventSentThrowsException() throws Exception {
    when(httpSessionMock.post(eq(EVENT_ENDPOINT), eq(event), any())).thenThrow(IOException.class);
    assertThat(forwarder.send(event).get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS).getResult()).isFalse();
  }

  @Test
  public void testEvictProjectOK() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = gson.toJson(key);
    when(httpSessionMock.post(eq(buildCacheEndpoint(Constants.PROJECTS)), eq(keyJson), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .evict(Constants.PROJECTS, key)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testEvictAccountsOK() throws Exception {
    Account.Id key = Account.id(123);
    String keyJson = gson.toJson(key);
    when(httpSessionMock.post(eq(buildCacheEndpoint(Constants.ACCOUNTS)), eq(keyJson), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .evict(Constants.ACCOUNTS, key)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testEvictGroupsOK() throws Exception {
    AccountGroup.Id key = AccountGroup.id(123);
    String keyJson = gson.toJson(key);
    String endpoint = buildCacheEndpoint(Constants.GROUPS);
    when(httpSessionMock.post(eq(endpoint), eq(keyJson), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .evict(Constants.GROUPS, key)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testEvictGroupsByIncludeOK() throws Exception {
    AccountGroup.UUID key = AccountGroup.uuid("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = gson.toJson(key);
    when(httpSessionMock.post(
            eq(buildCacheEndpoint(Constants.GROUPS_BYINCLUDE)), eq(keyJson), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .evict(Constants.GROUPS_BYINCLUDE, key)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testEvictGroupsMembersOK() throws Exception {
    AccountGroup.UUID key = AccountGroup.uuid("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = gson.toJson(key);
    when(httpSessionMock.post(eq(buildCacheEndpoint(Constants.GROUPS_MEMBERS)), eq(keyJson), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .evict(Constants.GROUPS_MEMBERS, key)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testEvictCacheFailed() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = gson.toJson(key);
    when(httpSessionMock.post(eq(buildCacheEndpoint(Constants.PROJECTS)), eq(keyJson), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .evict(Constants.PROJECTS, key)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testEvictCacheThrowsException() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = gson.toJson(key);
    when(httpSessionMock.post(eq(buildCacheEndpoint(Constants.PROJECTS)), eq(keyJson), any()))
        .thenThrow(IOException.class);
    assertThat(
            forwarder
                .evict(Constants.PROJECTS, key)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  private static String buildCacheEndpoint(String name) {
    return Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "cache", name);
  }

  @Test
  public void testAddToProjectListOK() throws Exception {
    String projectName = PROJECT_TO_ADD;
    when(httpSessionMock.post(eq(buildProjectListCacheEndpoint(projectName)), any(), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .addToProjectList(projectName)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testAddToProjectListFailed() throws Exception {
    String projectName = PROJECT_TO_ADD;
    when(httpSessionMock.post(eq(buildProjectListCacheEndpoint(projectName)), any(), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .addToProjectList(projectName)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testAddToProjectListThrowsException() throws Exception {
    String projectName = PROJECT_TO_ADD;
    when(httpSessionMock.post(eq(buildProjectListCacheEndpoint(projectName)), any(), any()))
        .thenThrow(IOException.class);
    assertThat(
            forwarder
                .addToProjectList(projectName)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testRemoveFromProjectListOK() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    when(httpSessionMock.delete(eq(buildProjectListCacheEndpoint(projectName)), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            forwarder
                .removeFromProjectList(projectName)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testRemoveToProjectListFailed() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    when(httpSessionMock.delete(eq(buildProjectListCacheEndpoint(projectName)), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            forwarder
                .removeFromProjectList(projectName)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testRemoveToProjectListThrowsException() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    when(httpSessionMock.delete(eq(buildProjectListCacheEndpoint(projectName)), any()))
        .thenThrow(IOException.class);
    assertThat(
            forwarder
                .removeFromProjectList(projectName)
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  private static String buildProjectListCacheEndpoint(String projectName) {
    return Joiner.on("/").join(buildCacheEndpoint(Constants.PROJECT_LIST), projectName);
  }

  @Test
  public void testRetryOnErrorThenSuccess() throws Exception {
    when(httpSessionMock.post(anyString(), anyString(), any()))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(
            forwarder
                .evict(Constants.PROJECT_LIST, new Object())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testRetryOnIoExceptionThenSuccess() throws Exception {
    when(httpSessionMock.post(anyString(), anyString(), any()))
        .thenThrow(new IOException())
        .thenThrow(new IOException())
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(
            forwarder
                .evict(Constants.PROJECT_LIST, new Object())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isTrue();
  }

  @Test
  public void testNoRetryAfterNonRecoverableException() throws Exception {
    when(httpSessionMock.post(anyString(), anyString(), any()))
        .thenThrow(new SSLException("Non Recoverable"))
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(
            forwarder
                .evict(Constants.PROJECT_LIST, new Object())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }

  @Test
  public void testFailureAfterMaxTries() throws Exception {
    when(httpSessionMock.post(anyString(), anyString(), any()))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR));

    assertThat(
            forwarder
                .evict(Constants.PROJECT_LIST, new Object())
                .get(TEST_TIMEOUT, TEST_TIMEOUT_UNITS)
                .getResult())
        .isFalse();
  }
}
