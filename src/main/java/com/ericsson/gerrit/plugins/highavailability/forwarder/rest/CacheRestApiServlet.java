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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.CacheEntry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.CacheNotFoundException;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedCacheEvictionHandler;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class CacheRestApiServlet extends AbstractRestApiServlet {
  private static final int CACHENAME_INDEX = 1;
  private static final long serialVersionUID = -1L;

  private final ForwardedCacheEvictionHandler forwardedCacheEvictionHandler;
  private final GsonParser gson;

  @Inject
  CacheRestApiServlet(
      ForwardedCacheEvictionHandler forwardedCacheEvictionHandler, GsonParser gson) {
    this.forwardedCacheEvictionHandler = forwardedCacheEvictionHandler;
    this.gson = gson;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    setHeaders(rsp);
    try {
      List<String> params = Splitter.on('/').splitToList(req.getPathInfo());
      String cacheName = params.get(CACHENAME_INDEX);
      String json = req.getReader().readLine();
      forwardedCacheEvictionHandler.evict(
          CacheEntry.from(cacheName, gson.fromJson(cacheName, json)));
      rsp.setStatus(SC_NO_CONTENT);
    } catch (CacheNotFoundException e) {
      log.atSevere().log("Failed to process eviction request: %s", e.getMessage());
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Failed to process eviction request");
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    }
  }
}
