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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

abstract class ReindexRunnable<T> implements Runnable {

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final AbstractIndexRestApiServlet.IndexName itemName;
  private final OneOffRequestContext ctx;
  private final IndexTs indexTs;
  private Timestamp newLastIndexTs;

  @Inject
  public ReindexRunnable(
      AbstractIndexRestApiServlet.IndexName itemName, IndexTs indexTs, OneOffRequestContext ctx) {
    this.itemName = itemName;
    this.ctx = ctx;
    this.indexTs = indexTs;
  }

  @Override
  public void run() {
    Optional<LocalDateTime> maybeIndexTs = indexTs.getUpdateTs(itemName);
    String itemNameString = itemName.name().toLowerCase();
    if (maybeIndexTs.isPresent()) {
      newLastIndexTs = maxTimestamp(newLastIndexTs, Timestamp.valueOf(maybeIndexTs.get()));
      log.atFine().log("Scanning for all the %ss after %s", itemNameString, newLastIndexTs);
      try (ManualRequestContext mctx = ctx.open()) {
        int count = 0;
        int errors = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        Timestamp maxFetchedItemTs = Timestamp.valueOf(newLastIndexTs.toLocalDateTime());
        for (T c : fetchItems()) {
          try {
            Optional<Timestamp> itemTs = indexIfNeeded(c, newLastIndexTs);
            if (itemTs.isPresent()) {
              count++;
              maxFetchedItemTs = maxTimestamp(maxFetchedItemTs, itemTs.get());
            }
          } catch (Exception e) {
            log.atSevere().withCause(e).log("Unable to reindex %s %s", itemNameString, c);
            errors++;
          }
        }
        newLastIndexTs = maxTimestamp(newLastIndexTs, maxFetchedItemTs);
        long elapsedNanos = stopwatch.stop().elapsed(TimeUnit.NANOSECONDS);
        if (count > 0) {
          log.atInfo().log(
              "%d %ss reindexed in %d msec (%d/sec), %d failed",
              count,
              itemNameString,
              elapsedNanos / 1000000L,
              (count * 1000L) / (elapsedNanos / 1000000L),
              errors);
        } else if (errors > 0) {
          log.atInfo().log("%d %ss failed to reindex", errors, itemNameString);
        } else {
          log.atFine().log("Scanning finished");
        }
        indexTs.update(itemName, newLastIndexTs.toLocalDateTime());
      } catch (Exception e) {
        log.atSevere().withCause(e).log("Unable to scan %ss", itemNameString);
      }
    }
  }

  private Timestamp maxTimestamp(Timestamp ts1, Timestamp ts2) {
    if (ts1 == null) {
      return ts2;
    }

    if (ts2 == null) {
      return ts1;
    }

    if (ts1.after(ts2)) {
      return ts1;
    }
    return ts2;
  }

  protected abstract Iterable<T> fetchItems() throws Exception;

  protected abstract Optional<Timestamp> indexIfNeeded(T item, Timestamp sinceTs);
}
