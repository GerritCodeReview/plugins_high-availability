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

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.common.base.Charsets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
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
      @EventGson Gson gson,
      ProcessorMetricsRegistry metricsRegistry,
      EventType postEventType,
      @Nullable EventType deleteEventType) {
    super(metricsRegistry, postEventType, deleteEventType);
    this.forwardedIndexingHandler = forwardedIndexingHandler;
    this.indexName = indexName;
    this.gson = gson;
    this.allowDelete = deleteEventType != null;
  }

  @Override
  protected boolean processPostRequest(HttpServletRequest req, HttpServletResponse rsp) {
    return process(req, rsp, Operation.INDEX);
  }

  @Override
  protected boolean processDeleteRequest(HttpServletRequest req, HttpServletResponse rsp) {
    if (!allowDelete) {
      sendError(
          rsp, SC_METHOD_NOT_ALLOWED, String.format("cannot delete %s from index", indexName));
      throw new NotImplementedException("Deletions not allowed for " + indexName);
    }
    return process(req, rsp, Operation.DELETE);
  }

  /**
   * Process the request by parsing the ID from the URL and invoking the indexing handler.
   *
   * @param req the HTTP request
   * @param rsp the HTTP response
   * @param operation the indexing operation to perform (INDEX or DELETE)
   * @return true if the operation was successful, false otherwise
   */
  private boolean process(HttpServletRequest req, HttpServletResponse rsp, Operation operation) {
    String path = req.getRequestURI();
    T id = parse(path.substring(path.lastIndexOf('/') + 1));

    try {
      forwardedIndexingHandler.index(id, operation, parseBody(req));
      rsp.setStatus(SC_NO_CONTENT);
      return true;
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      log.atSevere().withCause(e).log("Unable to update %s index", indexName);
      return false;
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
