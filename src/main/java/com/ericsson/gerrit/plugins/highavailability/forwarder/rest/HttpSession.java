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

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

class HttpSession {
  private final CloseableHttpClient httpClient;
  private final Provider<Set<PeerInfo>> peerInfo;

  @Inject
  HttpSession(CloseableHttpClient httpClient, Provider<Set<PeerInfo>> peerInfo) {
    this.httpClient = httpClient;
    this.peerInfo = peerInfo;
  }

  List<HttpResult> post(String endpoint) throws IOException {
    return post(endpoint, null);
  }

  List<HttpResult> post(String endpoint, String content) throws IOException {
    List<HttpPost> toPost =
        peerInfo
            .get()
            .stream()
            .map(peer -> createPost(peer, endpoint, content))
            .collect(Collectors.toList());

    List<HttpResult> results = new ArrayList<>();
    for (HttpPost post : toPost) {
      results.add(httpClient.execute(post, new HttpResponseHandler()));
    }
    return results;
  }

  private static HttpPost createPost(PeerInfo peer, String endpoint, String content) {
    HttpPost post = new HttpPost(peer.getDirectUrl() + endpoint);
    if (!Strings.isNullOrEmpty(content)) {
      post.addHeader("Content-Type", MediaType.JSON_UTF_8.toString());
      post.setEntity(new StringEntity(content, StandardCharsets.UTF_8));
    }
    return post;
  }

  List<HttpResult> delete(String endpoint) throws IOException {
    List<HttpDelete> toDelete =
        peerInfo
            .get()
            .stream()
            .map(peer -> createDelete(peer, endpoint))
            .collect(Collectors.toList());

    List<HttpResult> results = new ArrayList<>();
    for (HttpDelete delete : toDelete) {
      results.add(httpClient.execute(delete, new HttpResponseHandler()));
    }
    return results;
  }

  private static HttpDelete createDelete(PeerInfo peer, String endpoint) {
    return new HttpDelete(peer.getDirectUrl() + endpoint);
  }
}
