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

package com.ericsson.gerrit.plugins.highavailability.replication;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedGitReferenceUpdatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedHeadUpdatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedNewProjectCreatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedProjectDeletedEvent;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.HeadUpdatedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ReplicationListener
    implements GitReferenceUpdatedListener,
        NewProjectCreatedListener,
        ProjectDeletedListener,
        HeadUpdatedListener {

  private final Executor executor;
  private final Forwarder forwarder;
  private final String pluginName;
  private static final Logger log = LoggerFactory.getLogger(ReplicationListener.class);

  @Inject
  ReplicationListener(
      Forwarder forwarder, @ReplicationExecutor Executor executor, @PluginName String pluginName) {
    this.forwarder = forwarder;
    this.executor = executor;
    this.pluginName = pluginName;
    log.error("OFFLOADING:  " + "listener");
    log.info("OFFLOADING:  " + "listener");
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    log.error("OFFLOADING:  " + event.getProjectName());
    log.info("OFFLOADING:  " + event.getProjectName());
    if (!(event instanceof ForwardedGitReferenceUpdatedEvent)) {
      log.error("OFFLOADING:  " + event.getProjectName());
      log.info("OFFLOADING:  " + event.getProjectName());
      executor.execute(
          new ReplicationTask<>(event, ForwardedGitReferenceUpdatedEvent.class.getName()));
    }
  }

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event event) {
    if (!(event instanceof ForwardedNewProjectCreatedEvent)) {
      log.error("REPLICATIONOFFLOADING: " + event.getProjectName());
      executor.execute(
          new ReplicationTask<>(event, ForwardedNewProjectCreatedEvent.class.getName()));
    }
  }

  @Override
  public void onProjectDeleted(ProjectDeletedListener.Event event) {
    if (!(event instanceof ForwardedProjectDeletedEvent)) {
      log.error("REPLICATIONOFFLOADING: " + event.getProjectName());
      executor.execute(
          new ReplicationTask<>(
              new ForwardedProjectDeletedEvent(event.getProjectName()),
              ForwardedProjectDeletedEvent.class.getName()));
    }
  }

  @Override
  public void onHeadUpdated(HeadUpdatedListener.Event event) {
    if (!(event instanceof ForwardedHeadUpdatedEvent)) {
      log.error("REPLICATIONOFFLOADING: " + event.getProjectName());
      executor.execute(new ReplicationTask<>(event, ForwardedHeadUpdatedEvent.class.getName()));
    }
  }

  class ReplicationTask<T extends ProjectEvent> implements Runnable {
    private final T event;
    private final String type;

    ReplicationTask(T event, String type) {
      this.event = event;
      this.type = type;
    }

    @Override
    public void run() {
      forwarder.replicate(event, type);
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] Trigger replication of project '%s' (%s)",
          pluginName, event.getProjectName(), type);
    }
  }
}
