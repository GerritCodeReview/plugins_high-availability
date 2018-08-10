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
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RestForwarder implements Forwarder {
  private static final Logger log = LoggerFactory.getLogger(RestForwarder.class);

  private final HttpSession httpSession;
  private final String pluginRelativePath;
  private final Configuration cfg;

  @Inject
  RestForwarder(HttpSession httpClient, @PluginName String pluginName, Configuration cfg) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("/plugins", pluginName);
    this.cfg = cfg;
  }

  @Override
  public boolean indexAccount(final int accountId) {
    return new Request("index account", accountId) {
      @Override
      List<HttpResult> send() throws IOException {
        return httpSession.post(
            Joiner.on("/").join(pluginRelativePath, "index/account", accountId));
      }
    }.execute();
  }

  @Override
  public boolean indexChange(final int changeId) {
    return new Request("index change", changeId) {
      @Override
      List<HttpResult> send() throws IOException {
        return httpSession.post(buildIndexEndpoint(changeId));
      }
    }.execute();
  }

  @Override
  public boolean deleteChangeFromIndex(final int changeId) {
    return new Request("delete change", changeId) {
      @Override
      List<HttpResult> send() throws IOException {
        return httpSession.delete(buildIndexEndpoint(changeId));
      }
    }.execute();
  }

  @Override
  public boolean indexGroup(final String uuid) {
    return new Request("index group", uuid) {
      @Override
      List<HttpResult> send() throws IOException {
        return httpSession.post(Joiner.on("/").join(pluginRelativePath, "index/group", uuid));
      }
    }.execute();
  }

  private String buildIndexEndpoint(int changeId) {
    return Joiner.on("/").join(pluginRelativePath, "index/change", changeId);
  }

  @Override
  public boolean send(final Event event) {
    return new Request("send event", event.type) {
      @Override
      List<HttpResult> send() throws IOException {
        String serializedEvent =
            new GsonBuilder()
                .registerTypeAdapter(Supplier.class, new SupplierSerializer())
                .create()
                .toJson(event);
        return httpSession.post(Joiner.on("/").join(pluginRelativePath, "event"), serializedEvent);
      }
    }.execute();
  }

  @Override
  public boolean evict(final String cacheName, final Object key) {
    return new Request("invalidate cache " + cacheName, key) {
      @Override
      List<HttpResult> send() throws IOException {
        String json = GsonParser.toJson(cacheName, key);
        return httpSession.post(Joiner.on("/").join(pluginRelativePath, "cache", cacheName), json);
      }
    }.execute();
  }

  @Override
  public boolean addToProjectList(String projectName) {
    return new Request("Update project_list, add ", projectName) {
      @Override
      List<HttpResult> send() throws IOException {
        return httpSession.post(buildProjectListEndpoint(projectName));
      }
    }.execute();
  }

  @Override
  public boolean removeFromProjectList(String projectName) {
    return new Request("Update project_list, remove ", projectName) {
      @Override
      List<HttpResult> send() throws IOException {
        return httpSession.delete(buildProjectListEndpoint(projectName));
      }
    }.execute();
  }

  private String buildProjectListEndpoint(String projectName) {
    return Joiner.on("/")
        .join(pluginRelativePath, "cache", Constants.PROJECT_LIST, Url.encode(projectName));
  }

  private abstract class Request {
    private final String action;
    private final Object key;

    Request(String action, Object key) {
      this.action = action;
      this.key = key;
    }

    boolean execute() {
      log.debug("Executing {} {}", action, key);
      try {
        return send().stream().allMatch(HttpResult::isSuccessful);
      } catch (IOException e) {
        log.error("Failed to {} {} after {} tries; giving up", action, key, cfg.http().maxTries());
        return false;
      }
    }

    abstract List<HttpResult> send() throws IOException;
  }
}
