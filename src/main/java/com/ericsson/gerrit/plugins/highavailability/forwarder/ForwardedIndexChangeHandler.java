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
import com.ericsson.gerrit.plugins.highavailability.NoteDbMigration;
import com.ericsson.gerrit.plugins.highavailability.index.ChangeChecker;
import com.ericsson.gerrit.plugins.highavailability.index.ChangeCheckerImpl;
import com.ericsson.gerrit.plugins.highavailability.index.ChangeDb;
import com.ericsson.gerrit.plugins.highavailability.index.ForwardedIndexExecutor;
import com.google.common.base.Splitter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
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
  private final ChangeDb changeDb;
  private final ScheduledExecutorService indexExecutor;
  private final OneOffRequestContext oneOffCtx;
  private final int retryInterval;
  private final int maxTries;
  private final ChangeCheckerImpl.Factory changeCheckerFactory;
  private final NoteDbMigration noteDbMigration;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer,
      ChangeDb changeDb,
      Configuration config,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      OneOffRequestContext oneOffCtx,
      ChangeCheckerImpl.Factory changeCheckerFactory,
      NoteDbMigration noteDbMigration) {
    super(config.index());
    this.indexer = indexer;
    this.changeDb = changeDb;
    this.indexExecutor = indexExecutor;
    this.oneOffCtx = oneOffCtx;
    this.changeCheckerFactory = changeCheckerFactory;
    this.noteDbMigration = noteDbMigration;

    Index indexConfig = config.index();
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : 0;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
  }

  @Override
  protected void doIndex(String id, Optional<IndexEvent> indexEvent)
      throws IOException, OrmException {
    noteDbMigration.migrate(parseChangeId(id), parseProject(id));
    doIndex(id, indexEvent, 0);
  }

  private void doIndex(String id, Optional<IndexEvent> indexEvent, int retryCount)
      throws IOException, OrmException {
    try {
      ChangeChecker checker = changeCheckerFactory.create(id);
      Optional<ChangeNotes> changeNotes = checker.getChangeNotes();
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

      throw e;
    }
  }

  private void reindex(ChangeNotes notes) throws IOException, OrmException {
    try (ReviewDb db = changeDb.open()) {
      notes.reload();
      indexer.index(db, notes.getChange());
    }
  }

  private boolean rescheduleIndex(String id, Optional<IndexEvent> indexEvent, int retryCount) {
    if (retryCount > maxTries) {
      log.atSevere().log(
          "Change %s could not be indexed after %d retries. Change index could be stale.",
          id, retryCount);
      return false;
    }

    log.atWarning().log(
        "Retrying for the #%d time to index Change %s after %d msecs",
        retryCount, id, retryInterval);
    indexExecutor.schedule(
        () -> {
          try (ManualRequestContext ctx = oneOffCtx.open()) {
            Context.setForwardedEvent(true);
            doIndex(id, indexEvent, retryCount);
          } catch (Exception e) {
            log.atWarning().withCause(e).log("Change %s could not be indexed", id);
          }
        },
        retryInterval,
        TimeUnit.MILLISECONDS);
    return true;
  }

  @Override
  protected void doDelete(String id, Optional<IndexEvent> indexEvent) throws IOException {
    indexer.delete(parseChangeId(id));
    log.atFine().log("Change %s successfully deleted from index", id);
  }

  private static Change.Id parseChangeId(String id) {
    return new Change.Id(Integer.parseInt(getChangeIdParts(id).get(1)));
  }

  private static Project.NameKey parseProject(String id) {
    return new Project.NameKey(getChangeIdParts(id).get(0));
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
