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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedProjectListUpdateHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.gerrit.extensions.restapi.Url;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class ProjectListApiServlet extends AbstractRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final ForwardedProjectListUpdateHandler forwardedProjectListUpdateHandler;

  @Inject
  ProjectListApiServlet(
      ForwardedProjectListUpdateHandler forwardedProjectListUpdateHandler,
      ProcessorMetricsRegistry metricRegistry) {
    super(metricRegistry, EventType.PROJECT_LIST_ADDITION, EventType.PROJECT_LIST_DELETION);
    this.forwardedProjectListUpdateHandler = forwardedProjectListUpdateHandler;
  }

  @Override
  protected boolean processPostRequest(HttpServletRequest req, HttpServletResponse rsp) {
    return process(req, rsp, false);
  }

  @Override
  protected boolean processDeleteRequest(HttpServletRequest req, HttpServletResponse rsp) {
    return process(req, rsp, true);
  }

  private boolean process(HttpServletRequest req, HttpServletResponse rsp, boolean delete) {
    String requestURI = req.getRequestURI();
    String projectName = requestURI.substring(requestURI.lastIndexOf('/') + 1);
    try {
      forwardedProjectListUpdateHandler.update(Url.decode(projectName), delete);
      rsp.setStatus(SC_NO_CONTENT);
      return true;
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Unable to update project list");
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
      return false;
    }
  }
}
