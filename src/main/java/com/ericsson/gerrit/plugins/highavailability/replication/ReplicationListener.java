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

  private static final Logger log = LoggerFactory.getLogger(ReplicationListener.class);
  private final Executor executor;
  private final Forwarder forwarder;
  private final String pluginName;

  @Inject
  ReplicationListener(
      Forwarder forwarder, @ReplicationExecutor Executor executor, @PluginName String pluginName) {
    log.info("OFFLOADING--- set up listener");
    this.forwarder = forwarder;
    this.executor = executor;
    this.pluginName = pluginName;
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    log.info(
        "OFFLOADING --- gitupdated {} -- {}",
        event.getProjectName(),
        event.getClass().getCanonicalName());
    if (!(event instanceof ForwardedGitReferenceUpdatedEvent)) {
      log.info(
          "OFFLOADING inner {} -- {}", event.getProjectName(), event.getClass().getCanonicalName());
      executor.execute(
          new ReplicationTask<>(event, ForwardedGitReferenceUpdatedEvent.class.getName()));
    }
  }

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event event) {
    log.info(
        "OFFLOADING --- projectcreated {} -- {}",
        event.getProjectName(),
        event.getClass().getCanonicalName());
    if (!(event instanceof ForwardedNewProjectCreatedEvent)) {
      log.info(
          "OFFLOADING inner {} -- {}", event.getProjectName(), event.getClass().getCanonicalName());
      executor.execute(
          new ReplicationTask<>(event, ForwardedNewProjectCreatedEvent.class.getName()));
    }
  }

  @Override
  public void onProjectDeleted(ProjectDeletedListener.Event event) {
    log.info(
        "OFFLOADING --- projectdeleted: {} -- {}",
        event.getProjectName(),
        event.getClass().getCanonicalName());
    if (!(event instanceof ForwardedProjectDeletedEvent)) {
      log.info(
          "OFFLOADING inner {} -- {}", event.getProjectName(), event.getClass().getCanonicalName());
      executor.execute(
          new ReplicationTask<>(
              new ForwardedProjectDeletedEvent(event.getProjectName()),
              ForwardedProjectDeletedEvent.class.getName()));
    }
  }

  @Override
  public void onHeadUpdated(HeadUpdatedListener.Event event) {
    log.info(
        "OFFLOADING --- projectcreated: {} -- {}",
        event.getProjectName(),
        event.getClass().getCanonicalName());
    if (!(event instanceof ForwardedHeadUpdatedEvent)) {
      log.info(
          "OFFLOADING inner {} -- {}", event.getProjectName(), event.getClass().getCanonicalName());
      executor.execute(new ReplicationTask<>(event, ForwardedHeadUpdatedEvent.class.getName()));
    }
  }

  class ReplicationTask<T extends ProjectEvent> implements Runnable {
    private final T event;
    private final String cls;

    ReplicationTask(T event, String cls) {
      this.event = event;
      this.cls = cls;
    }

    @Override
    public void run() {
      forwarder.replicate(event, cls);
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] Trigger replication of project '%s' (%s)", pluginName, event.getProjectName(), cls);
    }
  }
}
