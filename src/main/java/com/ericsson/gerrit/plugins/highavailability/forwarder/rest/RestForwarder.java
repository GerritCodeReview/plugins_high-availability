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
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.base.Joiner;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

class RestForwarder implements Forwarder {
  enum RequestMethod {
    POST,
    DELETE
  }

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final HttpSession httpSession;
  private final String pluginRelativePath;
  private final Configuration cfg;
  private final Provider<Set<PeerInfo>> peerInfoProvider;
  private final GsonProvider gson;

  @Inject
  RestForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider,
      GsonProvider gson) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("plugins", pluginName);
    this.cfg = cfg;
    this.peerInfoProvider = peerInfoProvider;
    this.gson = gson;
  }

  @Override
  public List<Request> createIndexAccountRequests(final int accountId, IndexEvent event) {
    return createRequests(RequestMethod.POST, "index account", "index/account", accountId, event);
  }

  @Override
  public List<Request> createIndexChangeRequests(
      String projectName, int changeId, IndexEvent event) {
    return createRequests(
        RequestMethod.POST,
        "index change",
        "index/change",
        buildIndexEndpoint(projectName, changeId),
        event);
  }

  @Override
  public List<Request> createDeleteChangeFromIndexRequests(final int changeId, IndexEvent event) {
    return createRequests(
        RequestMethod.DELETE, "delete change", "index/change", buildIndexEndpoint(changeId), event);
  }

  @Override
  public List<Request> createIndexGroupRequests(final String uuid, IndexEvent event) {
    return createRequests(RequestMethod.POST, "index group", "index/group", uuid, event);
  }

  private String buildIndexEndpoint(int changeId) {
    return buildIndexEndpoint("", changeId);
  }

  private String buildIndexEndpoint(String projectName, int changeId) {
    String escapedProjectName = Url.encode(projectName);
    return escapedProjectName + '~' + changeId;
  }

  @Override
  public List<Request> createIndexProjectRequest(String projectName, IndexEvent event) {
    return createRequests(
        RequestMethod.POST, "index project", "index/project", Url.encode(projectName), event);
  }

  @Override
  public boolean send(final Event event) {
    return execute(RequestMethod.POST, "send event", "event", event.type, event);
  }

  @Override
  public boolean evict(final String cacheName, final Object key) {
    String json = gson.get().toJson(key);
    return execute(RequestMethod.POST, "invalidate cache " + cacheName, "cache", cacheName, json);
  }

  @Override
  public boolean addToProjectList(String projectName) {
    return execute(
        RequestMethod.POST,
        "Update project_list, add ",
        buildProjectListEndpoint(),
        Url.encode(projectName));
  }

  @Override
  public boolean removeFromProjectList(String projectName) {
    return execute(
        RequestMethod.DELETE,
        "Update project_list, remove ",
        buildProjectListEndpoint(),
        Url.encode(projectName));
  }

  private static String buildProjectListEndpoint() {
    return Joiner.on("/").join("cache", Constants.PROJECT_LIST);
  }

  private boolean execute(RequestMethod method, String action, String endpoint, Object id) {
    return execute(method, action, endpoint, id, null);
  }

  private boolean execute(
      RequestMethod method, String action, String endpoint, Object id, Object payload) {
    List<Request> requests = createRequests(method, action, endpoint, id, payload);
    return execute(requests);
  }

  @Override
  public Results executeOnce(List<Request> requests) {
    List<CompletableFuture<Result>> futures =
        requests.stream()
            .map(request -> CompletableFuture.supplyAsync(request::execute))
            .collect(Collectors.toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return Results.create(
        futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }

  private List<Request> createRequests(
      RequestMethod method, String action, String endpoint, Object id, Object payload) {
    return peerInfoProvider.get().stream()
        .map(peer -> createRequest(method, peer, action, endpoint, id, payload))
        .collect(Collectors.toList());
  }

  private boolean execute(List<Request> requests) {
    int retries = 0;
    Results result = executeOnce(requests);

    while (result.containsRetry()) {
      Results partialResults = retry(result.retryRequests(), retries++);
      result =
          result
              .appendSuccessfull(partialResults)
              .appendFailures(partialResults)
              .setRetries(partialResults);
    }

    return result.isSucessfull();
  }

  private Results retry(List<Request> requests, int retryNumber) {
    int maxTries = cfg.http().maxTries();
    if (retryNumber >= maxTries) {

      return Results.create(
          requests.stream()
              .map(
                  r -> {
                    log.atSevere().log(
                        "Failed to %s %s on %s after %d tries; giving up",
                        r.action, r.key, r.destination, maxTries);
                    return Result.create(r, RequestStatus.FAILURE);
                  })
              .collect(Collectors.toList()));
    }

    List<CompletableFuture<Result>> futures =
        requests.stream()
            .map(
                request ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            // TODO: In Java 9+ replace with CompletableFuture.delayedExecutor
                            Thread.sleep(cfg.http().retryInterval());
                            return request.execute();

                          } catch (InterruptedException ie) {
                            log.atSevere().withCause(ie).log(
                                "%s %s towards %s was interrupted; giving up",
                                request.action, request.key, request.destination);
                            Thread.currentThread().interrupt();
                            return Result.create(request, RequestStatus.FAILURE);
                          }
                        }))
            .collect(Collectors.toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return Results.create(
        futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }

  Request createRequest(
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
}
