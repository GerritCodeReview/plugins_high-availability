// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.inject.Inject;
import java.util.Optional;

class IndexEventHandler
    implements ChangeIndexedListener,
        AccountIndexedListener,
        GroupIndexedListener,
        ProjectIndexedListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final Forwarder forwarder;
  private final ChangeCheckerImpl.Factory changeChecker;
  private final CurrentRequestContext currCtx;

  @Inject
  IndexEventHandler(
      Forwarder forwarder, ChangeCheckerImpl.Factory changeChecker, CurrentRequestContext currCtx) {
    this.forwarder = forwarder;
    this.changeChecker = changeChecker;
    this.currCtx = currCtx;
  }

  @Override
  public void onAccountIndexed(int id) {
    currCtx.onlyWithContext(
        (ctx) -> {
          if (!Context.isForwardedEvent()) {
            forwarder.indexAccount(id, new IndexEvent());
          }
        });
  }

  @Override
  public void onChangeIndexed(String projectName, int id) {
    currCtx.onlyWithContext((ctx) -> executeIndexChangeTask(projectName, id));
  }

  @Override
  public void onAllChangesDeletedForProject(String projectName) {
    currCtx.onlyWithContext((ctx) -> executeAllChangesDeletedForProject(projectName));
  }

  private void executeAllChangesDeletedForProject(String projectName) {
    if (!Context.isForwardedEvent()) {
      forwarder.deleteAllChangesForProject(projectName);
    }
  }

  private void executeIndexChangeTask(String projectName, int id) {
    if (!Context.isForwardedEvent()) {
      String changeId = projectName + "~" + id;
      try {
        Optional<IndexEvent> indexEvent = changeChecker.create(changeId).newIndexEvent();
        if (indexEvent.isEmpty()) {
          return;
        }

        if (Thread.currentThread().getName().contains("Batch")) {
          forwarder.batchIndexChange(projectName, id, indexEvent.get());
        } else {
          forwarder.indexChange(projectName, id, indexEvent.get());
        }
      } catch (Exception e) {
        log.atWarning().withCause(e).log("Unable to create task to reindex change %s", changeId);
      }
    }
  }

  @Override
  public void onChangeDeleted(int id) {
    if (!Context.isForwardedEvent()) {
      forwarder.deleteChangeFromIndex(id, new IndexEvent());
    }
  }

  @Override
  public void onProjectIndexed(String projectName) {
    if (!Context.isForwardedEvent()) {
      forwarder.indexProject(projectName, new IndexEvent());
    }
  }

  @Override
  public void onGroupIndexed(String groupUUID) {
    if (!Context.isForwardedEvent()) {
      forwarder.indexGroup(groupUUID, new IndexEvent());
    }
  }
}
