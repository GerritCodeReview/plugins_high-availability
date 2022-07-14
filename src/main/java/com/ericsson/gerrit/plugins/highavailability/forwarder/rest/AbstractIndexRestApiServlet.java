// Copyright (C) 2017 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.common.base.Charsets;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractIndexRestApiServlet<T> extends AbstractRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final ForwardedIndexingHandler<T> forwardedIndexingHandler;
  private final IndexName indexName;
  private final boolean allowDelete;
  private final Gson gson;

  public enum IndexName {
    CHANGE,
    ACCOUNT,
    GROUP,
    PROJECT;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  abstract T parse(String id);

  AbstractIndexRestApiServlet(
      ForwardedIndexingHandler<T> forwardedIndexingHandler,
      IndexName indexName,
      boolean allowDelete,
      @EventGson Gson gson) {
    this.forwardedIndexingHandler = forwardedIndexingHandler;
    this.indexName = indexName;
    this.allowDelete = allowDelete;
    this.gson = gson;
  }

  AbstractIndexRestApiServlet(
      ForwardedIndexingHandler<T> forwardedIndexingHandler, IndexName indexName) {
    this(forwardedIndexingHandler, indexName, false, new Gson());
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    process(req, rsp, Operation.INDEX);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp) {
    if (!allowDelete) {
      sendError(
          rsp, SC_METHOD_NOT_ALLOWED, String.format("cannot delete %s from index", indexName));
    } else {
      process(req, rsp, Operation.DELETE);
    }
  }

  private void process(HttpServletRequest req, HttpServletResponse rsp, Operation operation) {
    setHeaders(rsp);
    String path = req.getRequestURI();
    T id = parse(path.substring(path.lastIndexOf('/') + 1));

    /**
     * Since [1] the change notes /meta refs are cached for all the incoming GET/HEAD REST APIs;
     * however, the high-availability indexing API is a POST served by a regular servlet and
     * therefore won't have any caching, which is problematic because of the high number of
     * associated refs lookups generated.
     *
     * <p>Simulate an incoming GET request for allowing caching of the /meta refs lookups.
     *
     * <p>[1] https://gerrit-review.googlesource.com/c/gerrit/+/334539/17
     */
    HttpServletRequestWrapper simulatedGetRequestForCaching =
        new HttpServletRequestWrapper(req) {
          @Override
          public String getMethod() {
            return "GET";
          }
        };

    try (PerThreadCache unused = PerThreadCache.create(simulatedGetRequestForCaching)) {
      forwardedIndexingHandler.index(id, operation, parseBody(req));
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      log.atSevere().withCause(e).log("Unable to update %s index", indexName);
    }
  }

  protected Optional<IndexEvent> parseBody(HttpServletRequest req) throws IOException {
    String contentType = req.getContentType();
    if (contentType != null && contentType.contains("application/json")) {
      try (Reader reader = new InputStreamReader(req.getInputStream(), Charsets.UTF_8)) {
        return Optional.ofNullable(gson.fromJson(reader, IndexEvent.class));
      }
    }
    return Optional.empty();
  }
}
