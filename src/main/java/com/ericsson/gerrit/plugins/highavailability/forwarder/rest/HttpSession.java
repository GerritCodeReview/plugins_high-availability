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

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.net.MediaType;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Optional;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

class HttpSession {
  private final CloseableHttpClient httpClient;
  private final Provider<Optional<PeerInfo>> peerInfo;
  private final Gson gson =
      new GsonBuilder().registerTypeAdapter(Supplier.class, new SupplierSerializer()).create();

  @Inject
  HttpSession(CloseableHttpClient httpClient, Provider<Optional<PeerInfo>> peerInfo) {
    this.httpClient = httpClient;
    this.peerInfo = peerInfo;
  }

  HttpResult post(String endpoint) throws IOException {
    return post(endpoint, null);
  }

  HttpResult post(String endpoint, Object content) throws IOException {
    HttpPost post = new HttpPost(getPeerInfo().getDirectUrl() + endpoint);
    if (content != null) {
      post.addHeader("Content-Type", MediaType.JSON_UTF_8.toString());
      post.setEntity(new StringEntity(jsonEncode(content), Charsets.UTF_8));
    }
    return httpClient.execute(post, new HttpResponseHandler());
  }

  private String jsonEncode(Object content) {
    if (content instanceof String) {
      return (String) content;
    }

    return gson.toJson(content);
  }

  HttpResult delete(String endpoint) throws IOException {
    return httpClient.execute(
        new HttpDelete(getPeerInfo().getDirectUrl() + endpoint), new HttpResponseHandler());
  }

  private PeerInfo getPeerInfo() throws PeerInfoNotAvailableException {
    PeerInfo info = peerInfo.get().orElse(null);
    if (info == null) {
      throw new PeerInfoNotAvailableException();
    }
    return info;
  }
}
