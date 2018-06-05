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
import com.ericsson.gerrit.plugins.highavailability.index.ChangeIndexedEvent;
import com.ericsson.gerrit.plugins.highavailability.index.ForwardedIndexExecutor;
import com.google.common.base.Splitter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
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
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ScheduledExecutorService indexExecutor;
  private final OneOffRequestContext oneOffCtx;
  private final int retryInterval;
  private final int maxTries;
  private final ChangeCheckerImpl.Factory changeCheckerFactory;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer,
      SchemaFactory<ReviewDb> schemaFactory,
      Configuration configuration,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      OneOffRequestContext oneOffCtx,
      ChangeCheckerImpl.Factory changeCheckerFactory) {
    this.indexer = indexer;
    this.schemaFactory = schemaFactory;
    this.indexExecutor = indexExecutor;
    this.oneOffCtx = oneOffCtx;
    this.changeCheckerFactory = changeCheckerFactory;

    Index indexConfig = configuration.index();
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : 0;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
  }

  @Override
  protected void doIndex(String id, Optional<?> body) throws IOException, OrmException {
    doIndex(id, body, 0);
  }

  private void doIndex(String id, Optional<?> body, int retryCount)
      throws IOException, OrmException {
    Optional<ChangeNotes> changeNotes = Optional.empty();
    Optional<ChangeIndexedEvent> indexEvent = body.map(e -> (ChangeIndexedEvent) e);
    try (ReviewDb db = schemaFactory.open()) {
      ChangeChecker checker = changeCheckerFactory.create(id);
      changeNotes = checker.getChangeNotes();
      if (changeNotes.isPresent()) {
        ChangeNotes notes = changeNotes.get();
        notes.reload();
        indexer.index(db, notes.getChange());

        if (checker.isChangeUpToDate(indexEvent)) {
          if (retryCount > 0) {
            log.warn("Change {} has been eventually indexed after {} attempt(s)", id, retryCount);
          } else {
            log.debug("Change {} successfully indexed", id);
          }
        } else {
          log.warn(
              "Change {} seems too old compared to the event timestamp (event-Ts={} >> change-Ts={})",
              id,
              indexEvent,
              checker);
          rescheduleIndex(id, body, retryCount + 1);
        }
      } else {
        log.warn(
            "Change {} could not be found in the local Git repository (eventTs={})",
            id,
            indexEvent);
      }
    } catch (Exception e) {
      if (!isCausedByNoSuchChangeException(e)) {
        throw e;
      }
      log.warn("Change {} was deleted, aborting forwarded indexing the change.", id);
    }
    if (!changeNotes.isPresent()) {
      indexer.delete(parseChangeId(id));
      log.warn("Change {} not found, deleted from index", id);
    }
  }

  private void rescheduleIndex(String id, Optional<?> body, int retryCount) {
    if (retryCount > maxTries) {
      log.error(
          "Change {} could not be indexed after {} retries. *CHANGE INDEX IS STALE*",
          id,
          retryCount);
      return;
    }

    log.warn(
        "Retrying for the #{} time to index Change {} after {} msecs",
        retryCount,
        id,
        retryInterval);
    indexExecutor.schedule(
        () -> {
          try (ManualRequestContext ctx = oneOffCtx.open()) {
            Context.setForwardedEvent(true);
            doIndex(id, body, retryCount);
          } catch (Exception e) {
            log.warn("Change {} could not be indexed", id, e);
          }
        },
        retryInterval,
        TimeUnit.MILLISECONDS);
  }

  @Override
  protected void doDelete(String id) throws IOException {
    indexer.delete(parseChangeId(id));
    log.debug("Change {} successfully deleted from index", id);
  }

  private static Change.Id parseChangeId(String id) {
    Change.Id changeId = new Change.Id(Integer.parseInt(Splitter.on("~").splitToList(id).get(1)));
    return changeId;
  }

  private static boolean isCausedByNoSuchChangeException(Throwable throwable) {
    while (throwable != null) {
      if (throwable instanceof NoSuchChangeException) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }
}
