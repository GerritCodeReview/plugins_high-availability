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

import static com.ericsson.gerrit.plugins.highavailability.forwarder.rest.RestForwarder.ALL_CHANGES_FOR_PROJECT;

import com.ericsson.gerrit.plugins.highavailability.index.ChangeChecker;
import com.ericsson.gerrit.plugins.highavailability.index.ChangeCheckerImpl;
import com.ericsson.gerrit.plugins.highavailability.index.ForwardedIndexExecutor;
import com.google.common.base.Splitter;
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
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Index a change using {@link ChangeIndexer}. This class is meant to be used on the receiving side
 * of the {@link Forwarder} since it will prevent indexed change to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same change id
 */
@Singleton
public class ForwardedIndexChangeHandler extends ForwardedIndexingHandler<String> {
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

  @Override
  protected CompletableFuture<Boolean> doIndex(String id, Optional<IndexEvent> indexEvent)
      throws IOException {
    return indexExecutor.getAsync(
        () -> {
          try (ManualRequestContext ctx = oneOffCtx.open()) {
            Context.setForwardedEvent(true);
            boolean result = indexOnce(id, indexEvent);
            return result;
          }
        });
  }

  private boolean indexOnce(String id, Optional<IndexEvent> indexEvent) throws Exception {
    try {
      ChangeChecker checker = changeCheckerFactory.create(id);
      Optional<ChangeNotes> changeNotes;
      try {
        changeNotes = checker.getChangeNotes();
      } catch (StorageException e) {
        log.atWarning().withCause(e).log("Change %s: cannot load change notes", id);
        changeNotes = Optional.empty();
      }
      if (changeNotes.isPresent()) {
        ChangeNotes notes = changeNotes.get();
        reindex(notes);

        if (checker.isChangeUpToDate(indexEvent)) {
          log.atFine().log("Change %s successfully indexed", id);
          return true;
        }

        log.atFine().log(
            "Change %s seems too old compared to the event timestamp (event-Ts=%s >> change-Ts=%s)",
            id, indexEvent, checker);
        return false;
      }

      log.atFine().log(
          "Change %s not present yet in local Git repository (event=%s)", id, indexEvent);
      return false;

    } catch (Exception e) {
      if (isCausedByNoSuchChangeException(e)) {
        indexer.delete(parseChangeId(id));
        log.atWarning().withCause(e).log("Error trying to index Change %s. Deleted from index", id);
        return true;
      }

      throw e;
    }
  }

  private void reindex(ChangeNotes notes) {
    notes.reload();
    indexer.reindexIfStale(notes.getProjectName(), notes.getChangeId());
  }

  @Override
  protected CompletableFuture<Boolean> doDelete(String id, Optional<IndexEvent> indexEvent)
      throws IOException {
    if (ALL_CHANGES_FOR_PROJECT.equals(extractChangeId(id))) {
      Project.NameKey projectName = parseProject(id);
      try {
        indexer.deleteAllForProject(projectName);
        log.atFine().log("All %s changes successfully deleted from index", projectName.get());
      } catch (RuntimeException e) {
        log.atFine().log(
            "An error occured during deletion of all %s changes from index", projectName.get());
        throw e;
      }
    } else {
      try {
        indexer.delete(parseChangeId(id));
        log.atFine().log("Change %s successfully deleted from index", id);
      } catch (RuntimeException e) {
        log.atFine().log("Change %s could not be deleted from index", id);
        throw e;
      }
    }
    return CompletableFuture.completedFuture(true);
  }

  private static Change.Id parseChangeId(String id) {
    return Change.id(Integer.parseInt(extractChangeId(id)));
  }

  private static Project.NameKey parseProject(String id) {
    return Project.nameKey(getChangeIdParts(id).get(0));
  }

  private static String extractChangeId(String id) {
    return getChangeIdParts(id).get(1);
  }

  private static List<String> getChangeIdParts(String id) {
    return Splitter.on("~").splitToList(id);
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
