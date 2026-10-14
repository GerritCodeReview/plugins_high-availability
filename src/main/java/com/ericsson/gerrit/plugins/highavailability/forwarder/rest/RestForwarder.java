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
import com.ericsson.gerrit.plugins.highavailability.forwarder.ChangeIndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwarderMetricsRegistry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.failsafe.FailsafeExecutor;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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
  private FailsafeExecutor<Result> executor;
  private final ForwarderMetricsRegistry metricsRegistry;

  @Inject
  RestForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider,
      @RestGson Gson gson,
      @RestForwarderExecutor FailsafeExecutor<Result> executor,
      ForwarderMetricsRegistry metricsRegistry) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("plugins", pluginName);
    this.cfg = cfg;
    this.peerInfoProvider = peerInfoProvider;
    this.gson = gson;
    this.executor = executor;
    this.metricsRegistry = metricsRegistry;
    this.executor.onComplete(
        ev -> {
          this.metricsRegistry.get(ev.getResult().type()).recordRetries(ev.getAttemptCount());
        });
  }

  @Override
  public CompletableFuture<Result> indexAccount(final int accountId) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_ACCOUNT_UPDATE,
        "index account",
        "index/account",
        accountId,
        Instant.now());
  }

  @Override
  public CompletableFuture<Result> indexChange(
      String projectName, int changeId, ChangeIndexEvent event) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_CHANGE_UPDATE,
        "index change",
        "index/change",
        buildIndexEndpoint(projectName, changeId),
        event,
        event.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Result> batchIndexChange(
      String projectName, int changeId, ChangeIndexEvent event) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_CHANGE_UPDATE_BATCH,
        "index change",
        "index/change/batch",
        buildIndexEndpoint(projectName, changeId),
        event,
        event.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Result> deleteChangeFromIndex(String projectName, final int changeId) {
    return execute(
        RequestMethod.DELETE,
        EventType.INDEX_CHANGE_DELETION,
        "delete change",
        "index/change",
        buildIndexEndpoint(projectName, changeId),
        Instant.now());
  }

  @Override
  public CompletableFuture<Result> indexGroup(final String uuid) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_GROUP_UPDATE,
        "index group",
        "index/group",
        uuid,
        Instant.now());
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
  public CompletableFuture<Result> indexProject(String projectName) {
    return execute(
        RequestMethod.POST,
        EventType.INDEX_PROJECT_UPDATE,
        "index project",
        "index/project",
        Url.encode(projectName),
        Instant.now());
  }

  @Override
  public CompletableFuture<Result> send(final Event event) {
    return execute(
        RequestMethod.POST,
        EventType.EVENT_SENT,
        "send event",
        "event",
        event.type,
        event,
        Instant.ofEpochSecond(event.eventCreatedOn));
  }

  @Override
  public CompletableFuture<Result> evict(final String cacheName, final Object key) {
    String json = gson.toJson(key);
    return execute(
        RequestMethod.POST,
        EventType.CACHE_EVICTION,
        "invalidate cache " + cacheName,
        "cache",
        cacheName,
        json,
        Instant.now());
  }

  @Override
  public CompletableFuture<Result> addToProjectList(String projectName) {
    return execute(
        RequestMethod.POST,
        EventType.PROJECT_LIST_ADDITION,
        "Update project_list, add ",
        buildProjectListEndpoint(),
        Url.encode(projectName),
        Instant.now());
  }

  @Override
  public CompletableFuture<Result> removeFromProjectList(String projectName) {
    return execute(
        RequestMethod.DELETE,
        EventType.PROJECT_LIST_DELETION,
        "Update project_list, remove ",
        buildProjectListEndpoint(),
        Url.encode(projectName),
        Instant.now());
  }

  @Override
  public CompletableFuture<Result> deleteAllChangesForProject(Project.NameKey projectName) {
    return execute(
        RequestMethod.DELETE,
        EventType.INDEX_CHANGE_DELETION_ALL_OF_PROJECT,
        "Delete all project changes from index",
        "index/change",
        buildAllChangesForProjectEndpoint(projectName.get()),
        Instant.now());
  }

  private static String buildProjectListEndpoint() {
    return Joiner.on("/").join("cache", Constants.PROJECT_LIST);
  }

  private CompletableFuture<Result> execute(
      RequestMethod method,
      EventType eventType,
      String action,
      String endpoint,
      Object id,
      Instant requestStart) {
    return execute(method, eventType, action, endpoint, id, null, requestStart);
  }

  private CompletableFuture<Result> execute(
      RequestMethod method,
      EventType eventType,
      String action,
      String endpoint,
      Object id,
      Object payload,
      Instant requestStart) {
    log.atFine().log("Scheduling forwarding of: %s %s %s", action, id, payload);
    return peerInfoProvider.get().stream()
        .map(
            peer ->
                createRequest(method, eventType, peer, action, endpoint, id, payload, requestStart))
        .map(r -> executor.getAsync(() -> r.execute()))
        .reduce(
            CompletableFuture.completedFuture(new Result(eventType, true)),
            (a, b) ->
                a.thenCombine(
                    b, (left, right) -> new Result(eventType, left.result() && right.result())))
        .thenApplyAsync(
            result -> {
              metricsRegistry.get(eventType).recordResult(result.result());
              metricsRegistry
                  .get(eventType)
                  .recordLatency(Duration.between(requestStart, Instant.now()).toMillis());
              return result;
            });
  }

  private Request createRequest(
      RequestMethod method,
      EventType eventType,
      PeerInfo peer,
      String action,
      String endpoint,
      Object id,
      Object payload,
      Instant createdOn) {
    String destination = peer.getDirectUrl();
    String payloadJson;
    if (payload == null) {
      payloadJson = null;
    } else if (payload instanceof String) {
      payloadJson = (String) payload;
    } else {
      payloadJson = gson.toJson(payload);
    }
    return new Request(
        eventType, method, action, endpoint, id, destination, payloadJson, createdOn);
  }

  private class Request {
    private final EventType eventType;
    private final RequestMethod method;
    private final String action;
    private final String endpoint;
    private final Object key;
    private final String destination;
    private final String payloadJson;
    private final Instant createdOn;

    private int execCnt;

    Request(
        EventType eventType,
        RequestMethod method,
        String action,
        String endpoint,
        Object key,
        String destination,
        String payloadJson,
        Instant createdOn) {
      this.method = method;
      this.eventType = eventType;
      this.action = action;
      this.endpoint = endpoint;
      this.key = key;
      this.destination = destination;
      this.payloadJson = payloadJson;
      this.createdOn = createdOn;
    }

    @Override
    public String toString() {
      return String.format("%s:%s => %s (try #%d)", action, key, destination, execCnt);
    }

    Result execute() {
      log.atFine().log("Executing %s %s towards %s: %s", action, key, destination, payloadJson);
      try {
        execCnt++;
        tryOnce();
        log.atFine().log("%s %s towards %s OK: %s", action, key, destination, payloadJson);
        return new Result(eventType, true);
      } catch (ForwardingException e) {
        int maxTries = cfg.http().maxTries();
        log.atFine().withCause(e).log(
            "Failed to %s %s on %s [%d/%d]", action, key, destination, execCnt, maxTries);
        if (!e.isRecoverable()) {
          log.atSevere().withCause(e).log(
              "%s %s towards %s failed with unrecoverable error; giving up",
              action, key, destination);
          return new Result(eventType, false, false);
        }
      }
      return new Result(eventType, false);
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

    HttpResult send() throws IOException {
      String request = Joiner.on("/").join(destination, pluginRelativePath, endpoint, key);
      switch (method) {
        case POST:
          return httpSession.post(request, payloadJson, createdOn);
        case DELETE:
        default:
          return httpSession.delete(request, createdOn);
      }
    }

    boolean isRecoverable(IOException e) {
      Throwable cause = e.getCause();
      return !(e instanceof SSLException
          || cause instanceof HttpException
          || cause instanceof ClientProtocolException);
    }
  }
}
