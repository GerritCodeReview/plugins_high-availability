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
import java.util.Set;
import javax.net.ssl.SSLException;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;

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
  private final RestForwarderScheduler scheduler;

  @Inject
  RestForwarder(
      HttpSession httpClient,
      @PluginName String pluginName,
      Configuration cfg,
      Provider<Set<PeerInfo>> peerInfoProvider,
      GsonProvider gson,
      RestForwarderScheduler scheduler) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("plugins", pluginName);
    this.cfg = cfg;
    this.peerInfoProvider = peerInfoProvider;
    this.gson = gson;
    this.scheduler = scheduler;
  }

  @Override
  public boolean indexAccount(final int accountId, IndexEvent event) {
    return execute(RequestMethod.POST, "index account", "index/account", accountId, event);
  }

  @Override
  public boolean indexChange(String projectName, int changeId, IndexEvent event) {
    return execute(
        RequestMethod.POST,
        "index change",
        "index/change",
        buildIndexEndpoint(projectName, changeId),
        event);
  }

  @Override
  public boolean deleteChangeFromIndex(final int changeId, IndexEvent event) {
    return execute(
        RequestMethod.DELETE, "delete change", "index/change", buildIndexEndpoint(changeId), event);
  }

  @Override
  public boolean indexGroup(final String uuid, IndexEvent event) {
    return execute(RequestMethod.POST, "index group", "index/group", uuid, event);
  }

  private String buildIndexEndpoint(int changeId) {
    return buildIndexEndpoint("", changeId);
  }

  private String buildIndexEndpoint(String projectName, int changeId) {
    String escapedProjectName = Url.encode(projectName);
    return escapedProjectName + '~' + changeId;
  }

  @Override
  public boolean indexProject(String projectName, IndexEvent event) {
    return execute(
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
    peerInfoProvider.get().stream()
        .map(peer -> createRequest(method, peer, action, endpoint, id, payload))
        .forEach(request -> scheduler.execute(request));
    return true;
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

  public abstract class Request {
    private final String action;
    private final Object key;
    private final String destination;

    private int execCnt;

    Request(String action, Object key, String destination) {
      this.action = action;
      this.key = key;
      this.destination = destination;
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
        if (execCnt >= maxTries) {
          log.atSevere().log(
              "Failed to %s %s on %s after %d tries; giving up",
              action, key, destination, maxTries);
          throw e;
        }
      }
      return false;
    }

    public int getExecCnt() {
      return execCnt;
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
