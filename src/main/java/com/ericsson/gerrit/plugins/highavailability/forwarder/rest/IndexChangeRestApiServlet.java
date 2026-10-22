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

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler.ChangeKey;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class IndexChangeRestApiServlet extends AbstractIndexRestApiServlet {
  private static final long serialVersionUID = -1L;

  private final ForwardedIndexChangeHandler handler;
  private final ChangeIndexEventParser parser;

  @Inject
  IndexChangeRestApiServlet(
      ForwardedIndexChangeHandler handler,
      ChangeIndexEventParser parser,
      ProcessorMetricsRegistry metricRegistry) {
    super(
        IndexName.CHANGE,
        metricRegistry,
        EventType.INDEX_CHANGE_UPDATE,
        EventType.INDEX_CHANGE_DELETION);
    this.handler = handler;
    this.parser = parser;
  }

  @Override
  protected boolean processPostRequest(HttpServletRequest req, HttpServletResponse rsp) {
    ChangeKey key = ChangeKeyExtractor.extract(req);
    return process(req, rsp, body -> handler.index(key.project(), key.id(), parser.parse(body)));
  }

  @Override
  protected boolean processDeleteRequest(HttpServletRequest req, HttpServletResponse rsp) {
    ChangeKey key = ChangeKeyExtractor.extract(req);
    if (key.id().get() == RestForwarder.ALL_CHANGES_FOR_PROJECT) {
      return process(req, rsp, _ -> handler.deleteAllForProject(key.project()));
    }
    return process(req, rsp, _ -> handler.delete(key.project(), key.id()));
  }
}
