// Copyright (C) 2023 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.commands;

import com.ericsson.gerrit.plugins.highavailability.forwarder.CacheEntry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ChangeIndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedCacheEvictionHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedEventHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexAccountHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexBatchChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedProjectListUpdateHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetrics;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Singleton
public class CommandProcessorImpl implements CommandProcessor {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final ForwardedIndexChangeHandler indexChangeHandler;
  private final ForwardedIndexBatchChangeHandler indexBatchChangeHandler;
  private final ForwardedIndexAccountHandler indexAccountHandler;
  private final ForwardedCacheEvictionHandler cacheEvictionHandler;
  private final ForwardedEventHandler eventHandler;
  private final ForwardedProjectListUpdateHandler projectListUpdateHandler;
  private final ProcessorMetricsRegistry metricRegistry;

  @Inject
  @VisibleForTesting
  public CommandProcessorImpl(
      ForwardedIndexChangeHandler indexChangeHandler,
      ForwardedIndexBatchChangeHandler indexBatchChangeHandler,
      ForwardedIndexAccountHandler indexAccountHandler,
      ForwardedCacheEvictionHandler cacheEvictionHandler,
      ForwardedEventHandler eventHandler,
      ForwardedProjectListUpdateHandler projectListUpdateHandler,
      ProcessorMetricsRegistry metricRegistry) {
    this.indexChangeHandler = indexChangeHandler;
    this.indexBatchChangeHandler = indexBatchChangeHandler;
    this.indexAccountHandler = indexAccountHandler;
    this.cacheEvictionHandler = cacheEvictionHandler;
    this.eventHandler = eventHandler;
    this.projectListUpdateHandler = projectListUpdateHandler;
    this.metricRegistry = metricRegistry;
  }

  @Override
  public CompletableFuture<Boolean> handle(Command cmd) {
    ProcessorMetrics metrics = metricRegistry.get(cmd.type);
    Instant startTime = Instant.now();

    CompletableFuture<Boolean> result;
    try {
      if (cmd instanceof IndexChange) {
        IndexChange indexChange = (IndexChange) cmd;
        ForwardedIndexChangeHandler handler =
            indexChange.isBatch() ? indexBatchChangeHandler : indexChangeHandler;
        result =
            indexChange instanceof IndexChange.Delete
                ? handler.delete(indexChange.getId())
                : handler.index(indexChange.getId(), toChangeIndexEvent(indexChange));

      } else if (cmd instanceof IndexAccount) {
        result = indexAccountHandler.index(Account.id(((IndexAccount) cmd).getId()));

      } else if (cmd instanceof EvictCache) {
        EvictCache evictCommand = (EvictCache) cmd;
        cacheEvictionHandler.evict(
            CacheEntry.from(evictCommand.getCacheName(), evictCommand.getKeyJson()));
        result = CompletableFuture.completedFuture(true);

      } else if (cmd instanceof PostEvent) {
        eventHandler.dispatch(((PostEvent) cmd).getEvent());
        result = CompletableFuture.completedFuture(true);

      } else if (cmd instanceof AddToProjectList) {
        projectListUpdateHandler.update(((AddToProjectList) cmd).getProjectName(), false);
        result = CompletableFuture.completedFuture(true);

      } else if (cmd instanceof RemoveFromProjectList) {
        projectListUpdateHandler.update(((RemoveFromProjectList) cmd).getProjectName(), true);
        result = CompletableFuture.completedFuture(true);

      } else {
        result = CompletableFuture.completedFuture(false);
      }
    } catch (Exception e) {
      log.atSevere().withCause(e).log("Error processing command %s", cmd);
      result = CompletableFuture.completedFuture(false);
    }

    return result.whenComplete(
        (success, t) -> {
          if (t != null) {
            log.atSevere().withCause(t).log("Error processing command %s", cmd);
          }
          metrics.record(cmd.eventCreatedOn, startTime, t == null && Boolean.TRUE.equals(success));
        });
  }

  private static ChangeIndexEvent toChangeIndexEvent(IndexChange cmd) {
    return new ChangeIndexEvent(cmd.eventCreatedOn, cmd.targetSha, cmd.metaSha);
  }
}
