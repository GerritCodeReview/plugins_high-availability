// Copyright (C) 2021 The Android Open Source Project
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
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ChangeIndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexBatchChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class IndexBatchChangeRestApiServlet extends AbstractIndexRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final ForwardedIndexBatchChangeHandler handler;
  private final Gson gson;

  @Inject
  IndexBatchChangeRestApiServlet(
      ForwardedIndexBatchChangeHandler handler,
      @RestGson Gson gson,
      ProcessorMetricsRegistry metricRegistry) {
    super(IndexName.CHANGE, metricRegistry, EventType.INDEX_CHANGE_UPDATE_BATCH, null);
    this.handler = handler;
    this.gson = gson;
  }

  @Override
  protected boolean processPostRequest(HttpServletRequest req, HttpServletResponse rsp) {
    String id = Url.decode(extractRawId(req));
    try {
      String body = readRequestBody(req);
      ForwardedMessageLogger.log(req, body);
      ChangeIndexEvent event = gson.fromJson(body, ChangeIndexEvent.class);
      if (event == null || event.metaSha == null) {
        sendError(rsp, SC_BAD_REQUEST, "metaSha is required for change index events");
        return false;
      }
      handler.index(id, event);
      rsp.setStatus(SC_NO_CONTENT);
      return true;
    } catch (Exception e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      log.atSevere().withCause(e).log("Unable to update change index");
      return false;
    }
  }
}
