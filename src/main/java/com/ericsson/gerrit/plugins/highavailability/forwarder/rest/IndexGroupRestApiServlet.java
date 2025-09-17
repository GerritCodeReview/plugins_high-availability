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

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexGroupHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class IndexGroupRestApiServlet extends AbstractIndexRestApiServlet<AccountGroup.UUID> {
  private static final long serialVersionUID = -1L;

  @Inject
  IndexGroupRestApiServlet(
      ForwardedIndexGroupHandler handler,
      @EventGson Gson gson,
      ProcessorMetricsRegistry metricRegistry) {
    super(handler, IndexName.GROUP, gson, metricRegistry, EventType.INDEX_GROUP_UPDATE, null);
  }

  @Override
  AccountGroup.UUID parse(String id) {
    return AccountGroup.uuid(id);
  }
}
