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
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractIndexRestApiServlet extends AbstractRestApiServlet {
  private static final long serialVersionUID = -1L;

  @FunctionalInterface
  interface IndexingOperation {
    void execute(String body) throws Exception;
  }

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

  private final IndexName indexName;

  AbstractIndexRestApiServlet(
      IndexName indexName,
      ProcessorMetricsRegistry metricsRegistry,
      EventType postEventType,
      @Nullable EventType deleteEventType) {
    super(metricsRegistry, postEventType, deleteEventType);
    this.indexName = indexName;
  }

  protected boolean process(HttpServletRequest req, HttpServletResponse rsp, IndexingOperation op) {
    try {
      String body = readRequestBody(req);
      ForwardedMessageLogger.log(req, body);
      op.execute(body);
      rsp.setStatus(SC_NO_CONTENT);
      return true;
    } catch (Exception e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      log.atSevere().withCause(e).log("Unable to update %s index", indexName);
      return false;
    }
  }

  @Override
  protected boolean processDeleteRequest(HttpServletRequest req, HttpServletResponse rsp) {
    sendError(rsp, SC_METHOD_NOT_ALLOWED, String.format("cannot delete %s from index", indexName));
    throw new NotImplementedException("Deletions not allowed for " + indexName);
  }

  protected static String extractRawId(HttpServletRequest req) {
    String path = req.getRequestURI();
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
