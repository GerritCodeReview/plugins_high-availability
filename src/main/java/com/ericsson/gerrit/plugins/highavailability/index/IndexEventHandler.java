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
import com.google.common.base.Objects;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

class IndexEventHandler
    implements ChangeIndexedListener,
        AccountIndexedListener,
        GroupIndexedListener,
        ProjectIndexedListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final ScheduledExecutorService executor;
  private final ScheduledExecutorService batchExecutor;
  private final Forwarder forwarder;
  private final String pluginName;
  private final Set<IndexTask> queuedTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final ChangeCheckerImpl.Factory changeChecker;
  private final CurrentRequestContext currCtx;

  @Inject
  IndexEventHandler(
      @IndexExecutor ScheduledExecutorService executor,
      @BatchIndexExecutor ScheduledExecutorService batchExecutor,
      @PluginName String pluginName,
      Forwarder forwarder,
      ChangeCheckerImpl.Factory changeChecker,
      CurrentRequestContext currCtx) {
    this.forwarder = forwarder;
    this.executor = executor;
    this.batchExecutor = batchExecutor;
    this.pluginName = pluginName;
    this.changeChecker = changeChecker;
    this.currCtx = currCtx;
  }

  @Override
  public void onAccountIndexed(int id) {
    currCtx.onlyWithContext(
        (ctx) -> {
          if (!Context.isForwardedEvent()) {
            IndexAccountTask task = new IndexAccountTask(id);
            if (queuedTasks.add(task)) {
              executor.execute(task);
            }
          }
        });
  }

  @Override
  public void onChangeIndexed(String projectName, int id) {
    currCtx.onlyWithContext((ctx) -> executeIndexChangeTask(projectName, id));
  }

  private void executeIndexChangeTask(String projectName, int id) {

    if (!Context.isForwardedEvent()) {
      String changeId = projectName + "~" + id;
      try {
        changeChecker
            .create(changeId)
            .newIndexEvent()
            .map(
                event -> {
                  if (Thread.currentThread().getName().contains("Batch")) {
                    return new BatchIndexChangeTask(projectName, id, event);
                  }
                  return new IndexChangeTask(projectName, id, event);
                })
            .ifPresent(
                task -> {
                  if (queuedTasks.add(task)) {
                    if (task instanceof BatchIndexChangeTask) {
                      batchExecutor.execute(task);
                    } else {
                      executor.execute(task);
                    }
                  }
                });
      } catch (Exception e) {
        log.atWarning().withCause(e).log("Unable to create task to reindex change %s", changeId);
      }
    }
  }

  @Override
  public void onChangeDeleted(int id) {
    if (!Context.isForwardedEvent()) {
      DeleteChangeTask task = new DeleteChangeTask(id, new IndexEvent());
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  @Override
  public void onProjectIndexed(String projectName) {
    if (!Context.isForwardedEvent()) {
      IndexProjectTask task = new IndexProjectTask(projectName);
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  @Override
  public void onGroupIndexed(String groupUUID) {
    if (!Context.isForwardedEvent()) {
      IndexGroupTask task = new IndexGroupTask(groupUUID);
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  abstract class IndexTask implements Runnable {
    protected final IndexEvent indexEvent;

    IndexTask() {
      indexEvent = new IndexEvent();
    }

    IndexTask(IndexEvent indexEvent) {
      this.indexEvent = indexEvent;
    }

    @Override
    public void run() {
      queuedTasks.remove(this);
      execute();
    }

    abstract CompletableFuture<Boolean> execute();

    abstract String indexId();
  }

  class IndexChangeTask extends IndexTask {
    private final int changeId;
    private final String projectName;

    IndexChangeTask(String projectName, int changeId, IndexEvent indexEvent) {
      super(indexEvent);
      this.projectName = projectName;
      this.changeId = changeId;
    }

    @Override
    public CompletableFuture<Boolean> execute() {
      return forwarder.indexChange(projectName, changeId, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexChangeTask.class, changeId);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexChangeTask)) {
        return false;
      }
      IndexChangeTask other = (IndexChangeTask) obj;
      return changeId == other.changeId;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index change %s in target instance", pluginName, changeId);
    }

    @Override
    String indexId() {
      return "change/" + changeId;
    }
  }

  class BatchIndexChangeTask extends IndexTask {
    private final int changeId;
    private final String projectName;

    BatchIndexChangeTask(String projectName, int changeId, IndexEvent indexEvent) {
      super(indexEvent);
      this.projectName = projectName;
      this.changeId = changeId;
    }

    @Override
    public CompletableFuture<Boolean> execute() {
      return forwarder.batchIndexChange(projectName, changeId, indexEvent);
    }

    @Override
    String indexId() {
      return "change/" + changeId;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexEventHandler.BatchIndexChangeTask.class, changeId);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexEventHandler.BatchIndexChangeTask)) {
        return false;
      }
      IndexEventHandler.BatchIndexChangeTask other = (IndexEventHandler.BatchIndexChangeTask) obj;
      return changeId == other.changeId;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index change %s in target instance", pluginName, changeId);
    }
  }

  class DeleteChangeTask extends IndexTask {
    private final int changeId;

    DeleteChangeTask(int changeId, IndexEvent indexEvent) {
      super(indexEvent);
      this.changeId = changeId;
    }

    @Override
    public CompletableFuture<Boolean> execute() {
      return forwarder.deleteChangeFromIndex(changeId, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(DeleteChangeTask.class, changeId);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof DeleteChangeTask)) {
        return false;
      }
      DeleteChangeTask other = (DeleteChangeTask) obj;
      return changeId == other.changeId;
    }

    @Override
    public String toString() {
      return String.format("[%s] Delete change %s in target instance", pluginName, changeId);
    }

    @Override
    String indexId() {
      return "change/" + changeId;
    }
  }

  class IndexAccountTask extends IndexTask {
    private final int accountId;

    IndexAccountTask(int accountId) {
      this.accountId = accountId;
    }

    @Override
    public CompletableFuture<Boolean> execute() {
      return forwarder.indexAccount(accountId, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(accountId);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexAccountTask)) {
        return false;
      }
      IndexAccountTask other = (IndexAccountTask) obj;
      return accountId == other.accountId;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index account %s in target instance", pluginName, accountId);
    }

    @Override
    String indexId() {
      return "account/" + accountId;
    }
  }

  class IndexGroupTask extends IndexTask {
    private final String groupUUID;

    IndexGroupTask(String groupUUID) {
      this.groupUUID = groupUUID;
    }

    @Override
    public CompletableFuture<Boolean> execute() {
      return forwarder.indexGroup(groupUUID, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexGroupTask.class, groupUUID);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexGroupTask)) {
        return false;
      }
      IndexGroupTask other = (IndexGroupTask) obj;
      return groupUUID.equals(other.groupUUID);
    }

    @Override
    public String toString() {
      return String.format("[%s] Index group %s in target instance", pluginName, groupUUID);
    }

    @Override
    String indexId() {
      return "group/" + groupUUID;
    }
  }

  class IndexProjectTask extends IndexTask {
    private final String projectName;

    IndexProjectTask(String projectName) {
      this.projectName = projectName;
    }

    @Override
    public CompletableFuture<Boolean> execute() {
      return forwarder.indexProject(projectName, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexProjectTask.class, projectName);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexProjectTask)) {
        return false;
      }
      IndexProjectTask other = (IndexProjectTask) obj;
      return projectName.equals(other.projectName);
    }

    @Override
    public String toString() {
      return String.format("[%s] Index project %s in target instance", pluginName, projectName);
    }

    @Override
    String indexId() {
      return "project/" + projectName;
    }
  }
}
