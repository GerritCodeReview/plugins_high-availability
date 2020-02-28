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

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.transport.ReceiveCommand;

public class ForwardedGitReferenceUpdatedEvent implements GitReferenceUpdatedListener.Event {
  private final String projectName;
  private final String ref;
  private final String oldObjectId;
  private final String newObjectId;
  private final ReceiveCommand.Type type;
  private final AccountInfo updater;

  ForwardedGitReferenceUpdatedEvent(
      Project.NameKey project,
      String ref,
      String oldObjectId,
      String newObjectId,
      ReceiveCommand.Type type,
      AccountInfo updater) {
    this.projectName = project.get();
    this.ref = ref;
    this.oldObjectId = oldObjectId;
    this.newObjectId = newObjectId;
    this.type = type;
    this.updater = updater;
  }

  @Override
  public String getProjectName() {
    return projectName;
  }

  @Override
  public String getRefName() {
    return ref;
  }

  @Override
  public String getOldObjectId() {
    return oldObjectId;
  }

  @Override
  public String getNewObjectId() {
    return newObjectId;
  }

  @Override
  public boolean isCreate() {
    return type == ReceiveCommand.Type.CREATE;
  }

  @Override
  public boolean isDelete() {
    return type == ReceiveCommand.Type.DELETE;
  }

  @Override
  public boolean isNonFastForward() {
    return type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD;
  }

  @Override
  public AccountInfo getUpdater() {
    return updater;
  }

  @Override
  public String toString() {
    return String.format(
        "%s[%s,%s: %s -> %s]",
        getClass().getSimpleName(), projectName, ref, oldObjectId, newObjectId);
  }

  @Override
  public NotifyHandling getNotify() {
    return NotifyHandling.ALL;
  }
}
