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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexProjectHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;

public class ProjectReindexRunnable extends ReindexRunnable<Project.NameKey> {

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final ProjectCache projectCache;
  private final boolean isAutoReindexEnabled;
  private final ForwardedIndexProjectHandler indexer;

  @Inject
  public ProjectReindexRunnable(
      ForwardedIndexProjectHandler indexer,
      IndexTs indexTs,
      OneOffRequestContext ctx,
      ProjectCache projectCache,
      Configuration cfg) {
    super(AbstractIndexRestApiServlet.IndexName.PROJECT, indexTs, ctx);
    this.isAutoReindexEnabled = cfg.autoReindex().autoProjectsReindex();
    this.projectCache = projectCache;
    this.indexer = indexer;
  }

  @Override
  protected Iterable<Project.NameKey> fetchItems() {
    return projectCache.all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(Project.NameKey projectName, Timestamp sinceTs) {
    Timestamp projectTs = projectCache.get(projectName).get().getProject().getRegisteredOn();
    if (isAutoReindexEnabled && projectTs.after(sinceTs)) {
      try {
        indexer.index(projectName, Operation.INDEX, Optional.empty());
      } catch (IOException e) {
        log.atSevere().withCause(e).log("Reindex failed");
      }
      return Optional.of(projectTs);
    }
    return Optional.empty();
  }
}
