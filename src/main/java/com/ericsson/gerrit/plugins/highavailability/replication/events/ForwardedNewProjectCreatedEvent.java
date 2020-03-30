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

package com.ericsson.gerrit.plugins.highavailability.replication.events;

import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.AbstractNoNotifyEvent;

public class ForwardedNewProjectCreatedEvent extends AbstractNoNotifyEvent
    implements NewProjectCreatedListener.Event {
  private final Project.NameKey name;
  private final String head;

  ForwardedNewProjectCreatedEvent(Project.NameKey name, String head) {
    this.name = name;
    this.head = head;
  }

  @Override
  public String getProjectName() {
    return name.get();
  }

  @Override
  public String getHeadName() {
    return head;
  }
}
