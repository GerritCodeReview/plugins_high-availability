// Copyright (C) 2018 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetrics;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractRestApiServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  protected static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final ProcessorMetrics postMetrics;
  private final ProcessorMetrics deleteMetrics;

  public AbstractRestApiServlet(
      ProcessorMetricsRegistry metricsRegistry,
      EventType postEventType,
      @Nullable EventType deleteEventType) {
    super();
    this.postMetrics = metricsRegistry.get(postEventType);
    if (deleteEventType != null) {
      this.deleteMetrics = metricsRegistry.get(deleteEventType);
    } else {
      this.deleteMetrics = null;
    }
  }

  protected static void setHeaders(HttpServletResponse rsp) {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding(UTF_8.name());
  }

  @Override
  public final void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    setHeaders(rsp);

    boolean success = processPostRequest(req, rsp);

    if (this.postMetrics != null) {
      this.postMetrics.recordResult(success);
    }
  }

  @Override
  public final void doDelete(HttpServletRequest req, HttpServletResponse rsp) {
    setHeaders(rsp);

    boolean success = processDeleteRequest(req, rsp);

    if (this.deleteMetrics != null) {
      this.deleteMetrics.recordResult(success);
    }
  }

  protected boolean processPostRequest(HttpServletRequest req, HttpServletResponse rsp) {
    throw new NotImplementedException("POST requests not implemented");
  }

  protected boolean processDeleteRequest(HttpServletRequest req, HttpServletResponse rsp) {
    throw new NotImplementedException("DELETE requests not implemented");
  }

  protected void sendError(HttpServletResponse rsp, int statusCode, String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Failed to send error messsage");
    }
  }
}
