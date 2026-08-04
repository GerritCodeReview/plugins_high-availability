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

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexProjectHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.Url;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class IndexProjectRestApiServlet extends AbstractIndexRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final ForwardedIndexProjectHandler handler;

  @Inject
  IndexProjectRestApiServlet(
      ForwardedIndexProjectHandler handler, ProcessorMetricsRegistry metricRegistry) {
    super(IndexName.PROJECT, metricRegistry, EventType.INDEX_PROJECT_UPDATE, null);
    this.handler = handler;
  }

  @Override
  protected boolean processPostRequest(HttpServletRequest req, HttpServletResponse rsp) {
    Project.NameKey projectName = Project.nameKey(Url.decode(extractRawId(req)));
    return process(req, rsp, _ -> handler.index(projectName, Operation.INDEX, Optional.empty()));
  }
}
