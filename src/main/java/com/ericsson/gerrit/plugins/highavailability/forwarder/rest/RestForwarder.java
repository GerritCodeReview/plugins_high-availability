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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardingException;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;

import java.io.IOException;

import javax.net.ssl.SSLException;

class RestForwarder implements Forwarder {

  private final HttpSession httpSession;
  private final String pluginRelativePath;

  @Inject
  RestForwarder(HttpSession httpClient,
      @PluginName String pluginName) {
    this.httpSession = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("/plugins", pluginName);
  }

  @Override
  public void indexChange(int changeId) throws ForwardingException {
    try {
      HttpResult result = httpSession.post(buildIndexEndpoint(changeId));
      if (!result.isSuccessful()) {
        throw new ForwardingException(true, "Unable to index change "
            + changeId + ". Cause: " + result.getMessage());
      }
    } catch (IOException e) {
      throw new ForwardingException(!(e instanceof SSLException),
          "Error trying to index change " + changeId, e);
    }
  }

  @Override
  public void deleteChangeFromIndex(int changeId) throws ForwardingException {
    try {
      HttpResult result = httpSession.delete(buildIndexEndpoint(changeId));
      if (!result.isSuccessful()) {
        throw new ForwardingException(true, "Unable to delete change from index"
            + " change " + changeId + ". Cause: " + result.getMessage());
      }
    } catch (IOException e) {
      throw new ForwardingException(!(e instanceof SSLException),
          "Error trying to delete change from index change" + changeId, e);
    }
  }

  private String buildIndexEndpoint(int changeId) {
    return Joiner.on("/").join(pluginRelativePath, "index", changeId);
  }

  @Override
  public void send(Event event) throws ForwardingException {
    String serializedEvent = new GsonBuilder()
        .registerTypeAdapter(Supplier.class, new SupplierSerializer()).create()
        .toJson(event);
    try {
      HttpResult result = httpSession.post(
          Joiner.on("/").join(pluginRelativePath, "event"), serializedEvent);
      if (!result.isSuccessful()) {
        throw new ForwardingException(true,
            "Unable to send event '" + event.type + "' " + result.getMessage());
      }
    } catch (IOException e) {
      throw new ForwardingException(!(e instanceof SSLException),
          "Error trying to send event " + event.type, e);
    }
  }

  @Override
  public void evict(String cacheName, Object key) throws ForwardingException {
    try {
      String json = GsonParser.toJson(cacheName, key);
      HttpResult result = httpSession.post(
          Joiner.on("/").join(pluginRelativePath, "cache", cacheName), json);
      if (!result.isSuccessful()) {
        throw new ForwardingException(true, "Unable to evict from cache '"
            + cacheName + "'. Cause: " + result.getMessage());
      }
    } catch (IOException e) {
      throw new ForwardingException(!(e instanceof SSLException),
          "Error trying to evict from cache " + cacheName, e);
    }
  }
}
