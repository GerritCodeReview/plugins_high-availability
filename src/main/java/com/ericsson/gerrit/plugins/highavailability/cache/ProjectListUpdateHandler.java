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

package com.ericsson.gerrit.plugins.highavailability.cache;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ProjectListUpdateHandler implements NewProjectCreatedListener, ProjectDeletedListener {

  private final Forwarder forwarder;

  @Inject
  public ProjectListUpdateHandler(Forwarder forwarder) {
    this.forwarder = forwarder;
  }

  @Override
  public void onNewProjectCreated(
      com.google.gerrit.extensions.events.NewProjectCreatedListener.Event event) {
    process(event, false);
  }

  @Override
  public void onProjectDeleted(
      com.google.gerrit.extensions.events.ProjectDeletedListener.Event event) {
    process(event, true);
  }

  private void process(ProjectEvent event, boolean delete) {
    if (!Context.isForwardedEvent()) {
      if (delete) {
        forwarder.removeFromProjectList(event.getProjectName());
      } else {
        forwarder.addToProjectList(event.getProjectName());
      }
    }
  }
}
