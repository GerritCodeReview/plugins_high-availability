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
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.GitReferenceUpdateTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.HeadUpdatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.NewProjectCreatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.ProjectDeletedTrigger;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is meant to be used on the receiving side of the {@link Forwarder}. */
@Singleton
public class ForwardedReplicationHandler {
  private static final Logger log = LoggerFactory.getLogger(ForwardedReplicationHandler.class);
  private final GitReferenceUpdateTrigger gitReferenceUpdateTrigger;
  private final HeadUpdatedTrigger headUpdatedTrigger;
  private final NewProjectCreatedTrigger newProjectCreatedTrigger;
  private final ProjectDeletedTrigger projectDeletedTrigger;

  @Inject
  public ForwardedReplicationHandler(
      GitReferenceUpdateTrigger gitReferenceUpdateTrigger,
      HeadUpdatedTrigger headUpdatedTrigger,
      NewProjectCreatedTrigger newProjectCreatedTrigger,
      ProjectDeletedTrigger projectDeletedTrigger) {
    this.gitReferenceUpdateTrigger = gitReferenceUpdateTrigger;
    this.headUpdatedTrigger = headUpdatedTrigger;
    this.newProjectCreatedTrigger = newProjectCreatedTrigger;
    this.projectDeletedTrigger = projectDeletedTrigger;
  }

  /**
   * Trigger a replication in the local node
   *
   * @param event The replication event
   */
  public void replicate(ProjectEvent event) {
    log.info(
        "OFFLOADING: replicating for event type"
            + event.getProjectName()
            + "---"
            + event.getClass().getCanonicalName());
    if (event instanceof ForwardedGitReferenceUpdatedEvent) {
      gitReferenceUpdateTrigger.trigger((ForwardedGitReferenceUpdatedEvent) event);
    } else if (event instanceof ForwardedNewProjectCreatedEvent) {
      newProjectCreatedTrigger.trigger((ForwardedNewProjectCreatedEvent) event);
    } else if (event instanceof ForwardedProjectDeletedEvent) {
      projectDeletedTrigger.trigger((ForwardedProjectDeletedEvent) event);
    } else if (event instanceof ForwardedHeadUpdatedEvent) {
      headUpdatedTrigger.trigger((ForwardedHeadUpdatedEvent) event);
    } else {
      log.error(
          "OFFLOADING: NONE found or event type"
              + event.getProjectName()
              + "---"
              + event.getClass().getCanonicalName());
    }
  }
}
