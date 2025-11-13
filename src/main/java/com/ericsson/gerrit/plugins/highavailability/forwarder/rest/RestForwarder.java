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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwarderMetricsRegistry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.failsafe.FailsafeExecutor;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLException;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;

public class RestForwarder implements Forwarder {
  enum RequestMethod {
    POST,
    DELETE
  }

  public static final String ALL_CHANGES_FOR_PROJECT = "0";

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final HttpSession httpSession;
  private final String pluginRelativePath;
  private final Configuration cfg;
  private final Provider<Set<PeerInfo>> peerInfoProvider;
  private final Gson gson;
  private FailsafeExecutor<Boolean> executor;
  private final ForwarderMetricsRegistry metricsRegistry;

  @Inject
  RestForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider,
      @EventGson Gson gson,
      @RestForwarderExecutor FailsafeExecutor<Boolean> executor,
      ForwarderMetricsRegistry metricsRegistry) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("plugins", pluginName);
    this.cfg = cfg;
    this.peerInfoProvider = peerInfoProvider;
    this.gson = gson;
    this.executor = executor;
    this.metricsRegistry = metricsRegistry;
    this.metricsRegistry.putAll(Arrays.asList(EventType.values()));
  }

  @Override
  public CompletableFuture<Boolean> indexAccount(final int accountId, IndexEvent event) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_ACCOUNT,
        "index account",
        "index/account",
        accountId,
        event);
  }

  @Override
  public CompletableFuture<Boolean> indexChange(
      String projectName, int changeId, IndexEvent event) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_CHANGE,
        "index change",
        "index/change",
        buildIndexEndpoint(projectName, changeId),
        event);
  }

  @Override
  public CompletableFuture<Boolean> batchIndexChange(
      String projectName, int changeId, IndexEvent event) {
    return execute(
        RequestMethod.POST,
        EventType.BATCH_INDEX_CHANGE,
        "index change",
        "index/change/batch",
        buildIndexEndpoint(projectName, changeId),
        event);
  }

  @Override
  public CompletableFuture<Boolean> deleteChangeFromIndex(final int changeId, IndexEvent event) {
    return execute(
        RequestMethod.DELETE,
        EventType.DELETE_CHANGE_FROM_INDEX,
        "delete change",
        "index/change",
        buildIndexEndpoint(changeId),
        event);
  }

  @Override
  public CompletableFuture<Boolean> indexGroup(final String uuid, IndexEvent event) {
    return execute(
        RequestMethod.POST, EventType.INDEX_GROUP, "index group", "index/group", uuid, event);
  }

  private String buildIndexEndpoint(int changeId) {
    return buildIndexEndpoint("", changeId);
  }

  private String buildIndexEndpoint(String projectName, int changeId) {
    String escapedProjectName = Url.encode(projectName);
    return escapedProjectName + '~' + changeId;
  }

  @VisibleForTesting
  public static String buildAllChangesForProjectEndpoint(String projectName) {
    String escapedProjectName = Url.encode(projectName);
    return escapedProjectName + '~' + ALL_CHANGES_FOR_PROJECT;
  }

  @Override
  public CompletableFuture<Boolean> indexProject(String projectName, IndexEvent event) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_PROJECT,
        "index project",
        "index/project",
        Url.encode(projectName),
        event);
  }

  @Override
  public CompletableFuture<Boolean> send(final Event event) {
    return execute(
        RequestMethod.POST, EventType.SEND_EVENT, "send event", "event", event.type, event);
  }

  @Override
  public CompletableFuture<Boolean> evict(final String cacheName, final Object key) {
    String json = gson.toJson(key);
    return execute(
        RequestMethod.POST,
        EventType.EVICT_CACHE,
        "invalidate cache " + cacheName,
        "cache",
        cacheName,
        json);
  }

  @Override
  public CompletableFuture<Boolean> addToProjectList(String projectName) {
    return execute(
        RequestMethod.POST,
        EventType.ADD_TO_PROJECT_LIST,
        "Update project_list, add ",
        buildProjectListEndpoint(),
        Url.encode(projectName));
  }

  @Override
  public CompletableFuture<Boolean> removeFromProjectList(String projectName) {
    return execute(
        RequestMethod.DELETE,
        EventType.REMOVE_FROM_PROJECT_LIST,
        "Update project_list, remove ",
        buildProjectListEndpoint(),
        Url.encode(projectName));
  }

  @Override
  public CompletableFuture<Boolean> deleteAllChangesForProject(Project.NameKey projectName) {
    return execute(
        RequestMethod.DELETE,
        EventType.DELETE_ALL_PROJECT_CHANGES_FROM_INDEX,
        "Delete all project changes from index",
        "index/change",
        buildAllChangesForProjectEndpoint(projectName.get()));
  }

  private static String buildProjectListEndpoint() {
    return Joiner.on("/").join("cache", Constants.PROJECT_LIST);
  }

  private CompletableFuture<Boolean> execute(
      RequestMethod method, EventType eventType, String action, String endpoint, Object id) {
    return execute(method, eventType, action, endpoint, id, null);
  }

  private CompletableFuture<Boolean> execute(
      RequestMethod method,
      EventType eventType,
      String action,
      String endpoint,
      Object id,
      Object payload) {
    return peerInfoProvider.get().stream()
        .map(peer -> createRequest(method, peer, action, endpoint, id, payload))
        .map(r -> executor.getAsync(() -> r.execute()))
        .reduce(
            CompletableFuture.completedFuture(true),
            (a, b) -> a.thenCombine(b, (left, right) -> left && right))
        .thenApplyAsync(
            result -> {
              metricsRegistry.get(eventType).recordResult(result);
              return result;
            });
  }

  private Request createRequest(
      RequestMethod method,
      PeerInfo peer,
      String action,
      String endpoint,
      Object id,
      Object payload) {
    String destination = peer.getDirectUrl();
    return new Request(action, id, destination) {
      @Override
      HttpResult send() throws IOException {
        String request = Joiner.on("/").join(destination, pluginRelativePath, endpoint, id);
        switch (method) {
          case POST:
            return httpSession.post(request, payload);
          case DELETE:
          default:
            return httpSession.delete(request);
        }
      }
    };
  }

  protected abstract class Request {
    private final String action;
    private final Object key;
    private final String destination;

    private int execCnt;

    Request(String action, Object key, String destination) {
      this.action = action;
      this.key = key;
      this.destination = destination;
    }

    @Override
    public String toString() {
      return String.format("%s:%s => %s (try #%d)", action, key, destination, execCnt);
    }

    boolean execute() throws ForwardingException {
      log.atFine().log("Executing %s %s towards %s", action, key, destination);
      try {
        execCnt++;
        tryOnce();
        log.atFine().log("%s %s towards %s OK", action, key, destination);
        return true;
      } catch (ForwardingException e) {
        int maxTries = cfg.http().maxTries();
        log.atFine().withCause(e).log(
            "Failed to %s %s on %s [%d/%d]", action, key, destination, execCnt, maxTries);
        if (!e.isRecoverable()) {
          log.atSevere().withCause(e).log(
              "%s %s towards %s failed with unrecoverable error; giving up",
              action, key, destination);
          throw e;
        }
      }
      return false;
    }

    void tryOnce() throws ForwardingException {
      try {
        HttpResult result = send();
        if (!result.isSuccessful()) {
          throw new ForwardingException(
              true, String.format("Unable to %s %s : %s", action, key, result.getMessage()));
        }
      } catch (IOException e) {
        throw new ForwardingException(isRecoverable(e), e.getMessage(), e);
      }
    }

    abstract HttpResult send() throws IOException;

    boolean isRecoverable(IOException e) {
      Throwable cause = e.getCause();
      return !(e instanceof SSLException
          || cause instanceof HttpException
          || cause instanceof ClientProtocolException);
    }
  }
}
