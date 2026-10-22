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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.ericsson.gerrit.plugins.highavailability.index.ChangeChecker;
import com.ericsson.gerrit.plugins.highavailability.index.ChangeCheckerImpl;
import com.ericsson.gerrit.plugins.highavailability.index.ForwardedIndexExecutor;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.failsafe.FailsafeExecutor;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Index a change using {@link ChangeIndexer}. This class is meant to be used on the receiving side
 * of the {@link Forwarder} since it will prevent indexed change to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same change id
 */
@Singleton
public class ForwardedIndexChangeHandler
    extends ForwardedIndexingHandler<ForwardedIndexChangeHandler.ChangeKey> {

  public record ChangeKey(Project.NameKey project, Change.Id id) {}

  private final ChangeIndexer indexer;
  private final FailsafeExecutor<Boolean> indexExecutor;
  private final OneOffRequestContext oneOffCtx;
  private final ChangeCheckerImpl.Factory changeCheckerFactory;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer,
      @ForwardedIndexExecutor FailsafeExecutor<Boolean> indexExecutor,
      OneOffRequestContext oneOffCtx,
      ChangeCheckerImpl.Factory changeCheckerFactory) {
    this.indexer = indexer;
    this.indexExecutor = indexExecutor;
    this.oneOffCtx = oneOffCtx;
    this.changeCheckerFactory = changeCheckerFactory;
  }

  public CompletableFuture<Boolean> index(
      Project.NameKey project, Change.Id changeId, ChangeIndexEvent event) throws Exception {
    return withInFlightGuard(
        new ChangeKey(project, changeId),
        () ->
            indexExecutor.getAsync(
                () ->
                    withForwardedEventFlag(
                        () -> {
                          try (ManualRequestContext ctx = oneOffCtx.open()) {
                            return indexOnce(project, changeId, event);
                          }
                        })));
  }

  public CompletableFuture<Boolean> delete(Project.NameKey project, Change.Id changeId)
      throws Exception {
    return withInFlightGuard(
        new ChangeKey(project, changeId),
        () -> {
          try {
            indexer.delete(project, changeId);
            log.atFine().log("Change %s~%s successfully deleted from index", project, changeId);
          } catch (RuntimeException e) {
            log.atFine().log("Change %s~%s could not be deleted from index", project, changeId);
            throw e;
          }
          return CompletableFuture.completedFuture(true);
        });
  }

  public CompletableFuture<Boolean> deleteAllForProject(Project.NameKey project) {
    try {
      indexer.deleteAllForProject(project);
      log.atFine().log("All %s changes successfully deleted from index", project.get());
    } catch (RuntimeException e) {
      log.atFine().log(
          "An error occured during deletion of all %s changes from index", project.get());
      throw e;
    }
    return CompletableFuture.completedFuture(true);
  }

  private boolean indexOnce(Project.NameKey project, Change.Id changeId, ChangeIndexEvent event)
      throws Exception {
    String compositeId = project.get() + "~" + changeId.get();
    try {
      ChangeChecker checker = changeCheckerFactory.create(compositeId);
      Optional<ChangeNotes> changeNotes;
      try {
        changeNotes = checker.getChangeNotes();
      } catch (StorageException e) {
        log.atWarning().withCause(e).log(
            "Change %s~%s: cannot load change notes", project, changeId);
        changeNotes = Optional.empty();
      }
      if (changeNotes.isPresent()) {
        if (!checker.isChangeUpToDate(event)) {
          log.atFine().log(
              "Change %s~%s is not yet up to date with the event (event=%s, change=%s)",
              project, changeId, event, checker);
          return false;
        }

        ChangeNotes notes = changeNotes.get();
        reindex(notes);
        log.atFine().log("Change %s~%s successfully indexed", project, changeId);
        return true;
      }

      log.atFine().log(
          "Change %s~%s not present yet in local Git repository (event=%s)",
          project, changeId, event);
      return false;

    } catch (Exception e) {
      if (isCausedByNoSuchChangeException(e)) {
        indexer.delete(project, changeId);
        log.atWarning().withCause(e).log(
            "Error trying to index Change %s~%s. Deleted from index", project, changeId);
        return true;
      }

      throw e;
    }
  }

  private void reindex(ChangeNotes notes) {
    var unused = notes.reload();
    var unusedResult = indexer.reindexIfStale(notes.getProjectName(), notes.getChangeId());
  }

  private static boolean isCausedByNoSuchChangeException(Throwable throwable) {
    Throwable cause = throwable;
    while (cause != null) {
      if (cause instanceof NoSuchChangeException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
