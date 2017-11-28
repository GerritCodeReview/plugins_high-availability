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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProjectListCacheUpdater implements ProjectEventHandler {
  private static final Logger log = LoggerFactory.getLogger(ProjectEventHandler.class);

  private final ProjectCache projectCache;

  @Inject
  ProjectListCacheUpdater(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  @Override
  public void handle(ProjectEvent event) {
    Project.NameKey name = event.getProjectNameKey();
    switch (event.getType()) {
      case "project-created":
        log.debug("Adding {} to project_list cache", name);
        projectCache.onCreateProject(name);
        break;
      case "project-deleted":
        log.debug("Removing {} from project_list cache", name);
        projectCache.remove(name);
        break;
      default:
        // do nothing
    }
  }
}
