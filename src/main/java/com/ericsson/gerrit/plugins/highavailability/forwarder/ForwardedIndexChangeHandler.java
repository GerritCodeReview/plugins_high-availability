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
import com.ericsson.gerrit.plugins.highavailability.event.ChangeIndexedEvent;
import com.ericsson.gerrit.plugins.highavailability.index.ForwardedIndexExecutor;
import com.google.common.base.Splitter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeFinder;
import com.google.gerrit.server.CommentsUtil;
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
import java.sql.Timestamp;
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
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ChangeFinder changeFinder;
  private final CommentsUtil commentsUtil;
  private final ScheduledExecutorService indexExecutor;
  private final OneOffRequestContext oneOffCtx;
  private final int retryInterval;
  private final int maxTries;
  private final int changeTsGraceInterval;

  @Inject
  ForwardedIndexChangeHandler(
      ChangeIndexer indexer,
      SchemaFactory<ReviewDb> schemaFactory,
      ChangeFinder changeFinder,
      CommentsUtil commentsUtil,
      Configuration configuration,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor,
      OneOffRequestContext oneOffCtx) {
    this.indexer = indexer;
    this.schemaFactory = schemaFactory;
    this.changeFinder = changeFinder;
    this.commentsUtil = commentsUtil;
    this.indexExecutor = indexExecutor;
    this.oneOffCtx = oneOffCtx;

    Index indexConfig = configuration.index();
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : 0;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
    this.changeTsGraceInterval = indexConfig != null ? indexConfig.changeTsGraceInterval() : 0;
  }

  @Override
  protected void doIndex(String id, Optional<?> maybeBody) throws IOException, OrmException {
    doIndex(id, maybeBody, 0);
  }

  private void doIndex(String id, Optional<?> maybeBody, int retryCount)
      throws IOException, OrmException {
    ChangeNotes change = null;
    Optional<ChangeIndexedEvent> indexEvent = maybeBody.map(e -> (ChangeIndexedEvent) e);
    try (ReviewDb db = schemaFactory.open()) {
      change = changeFinder.findOne(id);
      if (change != null) {
        Timestamp changeTs = computeLastChangeTs(db, change);

        if (isChangeUpToDate(changeTs, indexEvent, changeTsGraceInterval)) {
          indexer.index(db, change.getChange());
          if (retryCount > 0) {
            log.warn("Change {} has been eventually indexed after {} attempts)", id, retryCount);
          } else {
            log.debug("Change {} successfully indexed", id);
          }
        } else {
          log.warn(
              "Change {} seems too old compared compared to the event timestamp (event-Ts={} >> change-Ts={})",
              id,
              indexEvent,
              changeTs);
          rescheduleIndex(id, maybeBody, retryCount + 1);
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
    if (change == null) {
      indexer.delete(parseChangeId(id));
      log.warn("Change {} not found, deleted from index", id);
    }
  }

  private void rescheduleIndex(String id, Optional<?> maybeBody, int retryCount) {
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
            doIndex(id, maybeBody, retryCount);
          } catch (Exception e) {
            log.warn("Change {} could not be indexed", id, e);
          }
        },
        retryInterval,
        TimeUnit.MILLISECONDS);
  }

  private boolean isChangeUpToDate(
      Timestamp changeTs, Optional<ChangeIndexedEvent> indexEvent, int changeTsGraceInterval) {
    return indexEvent
        .map(e -> (changeTs.getTime() / 1000) >= (e.eventCreatedOn - changeTsGraceInterval))
        .orElse(true);
  }

  private Timestamp computeLastChangeTs(ReviewDb db, ChangeNotes change) {
    Timestamp changeTs = change.getChange().getLastUpdatedOn();
    List<Comment> comments = null;
    try {
      comments = commentsUtil.draftByChange(db, change);
    } catch (OrmException e) {
    }
    if (comments == null) {
      return changeTs;
    }

    for (Comment comment : comments) {
      Timestamp commentTs = comment.writtenOn;
      changeTs = commentTs.after(changeTs) ? commentTs : changeTs;
    }
    return changeTs;
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
