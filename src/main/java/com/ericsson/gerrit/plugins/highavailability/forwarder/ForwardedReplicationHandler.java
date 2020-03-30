// Copyright (C) 2020 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedGitReferenceUpdatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedHeadUpdatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedNewProjectCreatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedProjectDeletedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.GitReferenceUpdatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.HeadUpdatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.NewProjectCreatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.ProjectDeletedTrigger;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** This class is meant to be used on the receiving side of the {@link Forwarder}. */
@Singleton
public class ForwardedReplicationHandler {

  private final GitReferenceUpdatedTrigger gitReferenceUpdated;
  private final HeadUpdatedTrigger headUpdated;
  private final NewProjectCreatedTrigger newProjectCreated;
  private final ProjectDeletedTrigger projectDeleted;

  @Inject
  public ForwardedReplicationHandler(
      GitReferenceUpdatedTrigger gitReferenceUpdated,
      HeadUpdatedTrigger headUpdated,
      NewProjectCreatedTrigger newProjectCreated,
      ProjectDeletedTrigger projectDeleted) {
    this.gitReferenceUpdated = gitReferenceUpdated;
    this.headUpdated = headUpdated;
    this.newProjectCreated = newProjectCreated;
    this.projectDeleted = projectDeleted;
  }

  /**
   * Trigger a replication in the local node.
   *
   * @param event The replication event.
   * @throws IllegalArgumentException if event type is not supported.
   */
  public void replicate(ProjectEvent event) {
    if (event instanceof ForwardedGitReferenceUpdatedEvent) {
      gitReferenceUpdated.triggerEvent((ForwardedGitReferenceUpdatedEvent) event);
    } else if (event instanceof ForwardedNewProjectCreatedEvent) {
      newProjectCreated.triggerEvent((ForwardedNewProjectCreatedEvent) event);
    } else if (event instanceof ForwardedProjectDeletedEvent) {
      projectDeleted.triggerEvent((ForwardedProjectDeletedEvent) event);
    } else if (event instanceof ForwardedHeadUpdatedEvent) {
      headUpdated.triggerEvent((ForwardedHeadUpdatedEvent) event);
    } else {
      throw new IllegalArgumentException(
          "Unsupported ProjectEvent type " + event.getClass().getSimpleName());
    }
  }
}
