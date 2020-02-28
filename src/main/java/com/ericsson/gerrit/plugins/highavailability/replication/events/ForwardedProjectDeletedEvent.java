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

import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.AbstractNoNotifyEvent;

public class ForwardedProjectDeletedEvent extends AbstractNoNotifyEvent
    implements ProjectDeletedListener.Event {
  private final Project.NameKey nameKey;

  public ForwardedProjectDeletedEvent(String nameKey) {
    this.nameKey = new Project.NameKey(nameKey);
  }

  @Override
  public String getProjectName() {
    return nameKey.get();
  }
}
