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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.Index;
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
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Index a change using {@link ChangeIndexer}. This class is meant to be used on the receiving side
 * of the {@link Forwarder} since it will prevent indexed change to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same change id
 */
@Singleton
public class ForwardedIndexChangeHandler extends ForwardedIndexingHandler<String> {
  private final ChangeIndexer indexer;
  private final ScheduledExecutorService indexExecutor;
  private final OneOffRequestContext oneOffCtx;
  private final Duration retryInterval;
  private final int maxTries;
  private final ChangeCheckerImpl.Factory changeCheckerFactory;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer,
      Configuration config,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      OneOffRequestContext oneOffCtx,
      ChangeCheckerImpl.Factory changeCheckerFactory) {
    this.indexer = indexer;
    this.indexExecutor = indexExecutor;
    this.oneOffCtx = oneOffCtx;
    this.changeCheckerFactory = changeCheckerFactory;

    Index indexConfig = config.index();
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : Duration.ZERO;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
  }

  @Override
  protected void doIndex(String id, Optional<IndexEvent> indexEvent) throws IOException {
    doIndex(id, indexEvent, 0);
  }

  private void doIndex(String id, Optional<IndexEvent> indexEvent, int retryCount)
      throws IOException {
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
          if (retryCount > 0) {
            log.atWarning().log(
                "Change %s has been eventually indexed after %d attempt(s)", id, retryCount);
          } else {
            log.atFine().log("Change %s successfully indexed", id);
          }
        } else {
          log.atWarning().log(
              "Change %s seems too old compared to the event timestamp (event-Ts=%s >> change-Ts=%s)",
              id, indexEvent, checker);
          rescheduleIndex(id, indexEvent, retryCount + 1);
        }
      } else {
        log.atWarning().log(
            "Change %s not present yet in local Git repository (event=%s) after %d attempt(s)",
            id, indexEvent, retryCount);
        if (!rescheduleIndex(id, indexEvent, retryCount + 1)) {
          log.atSevere().log(
              "Change %s could not be found in the local Git repository (event=%s)",
              id, indexEvent);
        }
      }
    } catch (Exception e) {
      if (isCausedByNoSuchChangeException(e)) {
        indexer.delete(parseChangeId(id));
        log.atWarning().withCause(e).log("Error trying to index Change %s. Deleted from index", id);
        return;
      }

      if (isCausedByStorageException(e)) {
        rescheduleIndex(id, indexEvent, retryCount + 1);
        log.atInfo().withCause(e).log("Waiting for the patchset of Change %s to sync", id);
        return;
      }
      throw e;
    }
  }

  private void reindex(ChangeNotes notes) {
    notes.reload();
    indexer.index(notes.getChange());
  }

  private boolean rescheduleIndex(String id, Optional<IndexEvent> indexEvent, int retryCount) {
    if (retryCount > maxTries) {
      log.atSevere().log(
          "Change %s could not be indexed after %d retries. Change index could be stale.",
          id, retryCount);
      return false;
    }

    log.atWarning().log(
        "Retrying for the #%d time to index Change %s after %s", retryCount, id, retryInterval);
    indexExecutor.schedule(
        () -> {
          try (ManualRequestContext ctx = oneOffCtx.open()) {
            Context.setForwardedEvent(true);
            doIndex(id, indexEvent, retryCount);
          } catch (Exception e) {
            log.atWarning().withCause(e).log("Change %s could not be indexed", id);
          }
        },
        retryInterval.toMillis(),
        TimeUnit.MILLISECONDS);
    return true;
  }

  @Override
  protected void doDelete(String id, Optional<IndexEvent> indexEvent) throws IOException {
    indexer.delete(parseChangeId(id));
    log.atFine().log("Change %s successfully deleted from index", id);
  }

  private static Change.Id parseChangeId(String id) {
    return Change.id(Integer.parseInt(getChangeIdParts(id).get(1)));
  }

  private static Project.NameKey parseProject(String id) {
    return Project.nameKey(getChangeIdParts(id).get(0));
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

  private static boolean isCausedByStorageException(Throwable throwable) {
    return throwable instanceof StorageException;
  }
}
