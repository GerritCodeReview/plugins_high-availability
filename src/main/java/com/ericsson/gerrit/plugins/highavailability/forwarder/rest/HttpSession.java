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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Provider;

class HttpSession {
  private final CloseableHttpClient httpClient;
  private final Provider<PeerInfo> peerInfo;

  @Inject
  HttpSession(CloseableHttpClient httpClient,
      Provider<PeerInfo> peerInfo) {
    this.httpClient = httpClient;
    this.peerInfo = peerInfo;
  }

  HttpResult post(String endpoint) throws IOException {
    return post(endpoint, null);
  }

  HttpResult post(String endpoint, String content) throws IOException {
    HttpPost post = new HttpPost(peerInfo.get().getDirectUrl() + endpoint);
    if (!Strings.isNullOrEmpty(content)) {
      post.addHeader("Content-Type", MediaType.JSON_UTF_8.toString());
      post.setEntity(new StringEntity(content, StandardCharsets.UTF_8));
    }
    return httpClient.execute(post, new HttpResponseHandler());
  }

  HttpResult delete(String endpoint) throws IOException {
    return httpClient.execute(
        new HttpDelete(peerInfo.get().getDirectUrl() + endpoint),
        new HttpResponseHandler());
  }
}
