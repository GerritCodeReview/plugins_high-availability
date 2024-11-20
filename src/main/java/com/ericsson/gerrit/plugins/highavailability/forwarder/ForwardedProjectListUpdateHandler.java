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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;

import java.io.IOException;

/**
 * Update project list cache. This class is meant to be used on the receiving side of the {@link
 * Forwarder} since it will prevent project list updates to be forwarded again causing an infinite
 * forwarding loop between the 2 nodes.
 */
@Singleton
public class ForwardedProjectListUpdateHandler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final ProjectCache projectCache;
  private final GitRepositoryManager repoMgr;

  @Inject
  ForwardedProjectListUpdateHandler(ProjectCache projectCache, GitRepositoryManager repoMgr) {
    this.projectCache = projectCache;
    this.repoMgr = repoMgr;
  }

  /**
   * Update the project list, update will not be forwarded to the other node
   *
   * @param projectName the name of the project to add or remove.
   * @param remove true to remove, false to add project.
   * @throws IOException
   */
  public void update(String projectName, boolean remove) throws IOException {
    Project.NameKey projectKey = Project.nameKey(projectName);
    try {
      Context.setForwardedEvent(true);
      if (remove) {
        projectCache.remove(projectKey);
        removeFromRepositoryCache(projectKey);
        log.atFine().log("Removed %s from project list", projectName);
      } else {
        projectCache.onCreateProject(projectKey);
        log.atFine().log("Added %s to project list", projectName);
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private void removeFromRepositoryCache(Project.NameKey projectKey) {
    try (Repository repo = repoMgr.openRepository(projectKey)) {
      RepositoryCache.unregister(repo);
    } catch (IOException e) {
	// The repository does not exist: nothing to do
    }
  }
}
