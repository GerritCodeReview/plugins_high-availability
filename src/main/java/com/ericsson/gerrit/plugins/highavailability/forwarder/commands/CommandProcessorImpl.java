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
      result =
          switch (cmd) {
            case IndexChange.Delete delete -> indexChangeHandler.delete(delete.getId());
            case IndexChange.BatchUpdate batchUpdate ->
                indexBatchChangeHandler.index(batchUpdate.getId(), toChangeIndexEvent(batchUpdate));
            case IndexChange.Update update ->
                indexChangeHandler.index(update.getId(), toChangeIndexEvent(update));
            case IndexAccount indexAccount ->
                indexAccountHandler.index(Account.id(indexAccount.getId()));
            case EvictCache evictCache -> {
              cacheEvictionHandler.evict(
                  CacheEntry.from(evictCache.getCacheName(), evictCache.getKeyJson()));
              yield CompletableFuture.completedFuture(true);
            }
            case PostEvent postEvent -> {
              eventHandler.dispatch(postEvent.getEvent());
              yield CompletableFuture.completedFuture(true);
            }
            case AddToProjectList addToProjectList -> {
              projectListUpdateHandler.update(addToProjectList.getProjectName(), false);
              yield CompletableFuture.completedFuture(true);
            }
            case RemoveFromProjectList removeFromProjectList -> {
              projectListUpdateHandler.update(removeFromProjectList.getProjectName(), true);
              yield CompletableFuture.completedFuture(true);
            }
            default -> CompletableFuture.completedFuture(false);
          };
    } catch (Exception e) {
      log.atSevere().withCause(e).log("Error processing command %s", cmd);
      result = CompletableFuture.completedFuture(false);
    }

    return result.whenComplete(
        (success, ex) -> {
          if (ex != null) {
            log.atSevere().withCause(ex).log("Error processing command %s", cmd);
          }
          metrics.record(cmd.eventCreatedOn, startTime, ex == null && Boolean.TRUE.equals(success));
        });
  }

  private static ChangeIndexEvent toChangeIndexEvent(IndexChange cmd) {
    return new ChangeIndexEvent(cmd.eventCreatedOn, cmd.targetSha, cmd.metaSha);
  }
}
