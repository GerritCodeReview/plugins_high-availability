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
import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedCacheEvictionHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedEventHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexAccountHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexBatchChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedProjectListUpdateHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class CommandProcessor {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final ForwardedIndexChangeHandler indexChangeHandler;
  private final ForwardedIndexBatchChangeHandler indexBatchChangeHandler;
  private final ForwardedIndexAccountHandler indexAccountHandler;
  private final ForwardedCacheEvictionHandler cacheEvictionHandler;
  private final ForwardedEventHandler eventHandler;
  private final ForwardedProjectListUpdateHandler projectListUpdateHandler;

  @Inject
  @VisibleForTesting
  public CommandProcessor(
      ForwardedIndexChangeHandler indexChangeHandler,
      ForwardedIndexBatchChangeHandler indexBatchChangeHandler,
      ForwardedIndexAccountHandler indexAccountHandler,
      ForwardedCacheEvictionHandler cacheEvictionHandler,
      ForwardedEventHandler eventHandler,
      ForwardedProjectListUpdateHandler projectListUpdateHandler) {
    this.indexChangeHandler = indexChangeHandler;
    this.indexBatchChangeHandler = indexBatchChangeHandler;
    this.indexAccountHandler = indexAccountHandler;
    this.cacheEvictionHandler = cacheEvictionHandler;
    this.eventHandler = eventHandler;
    this.projectListUpdateHandler = projectListUpdateHandler;
  }

  public boolean handle(Command cmd) {
    Context.setForwardedEvent(true);
    try {

      if (cmd instanceof IndexChange) {
        IndexChange indexChange = (IndexChange) cmd;
        Operation op = getOperation(indexChange);
        try {
          ForwardedIndexChangeHandler handler =
              indexChange.isBatch() ? indexBatchChangeHandler : indexChangeHandler;
          handler.index(indexChange.getId(), op, Optional.empty());
          log.atFine().log(
              "Change index %s on change %s done", op.name().toLowerCase(), indexChange.getId());
        } catch (Exception e) {
          log.atSevere().withCause(e).log(
              "Change index %s on change %s failed", op.name().toLowerCase(), indexChange.getId());
          return false;
        }

      } else if (cmd instanceof IndexAccount) {
        IndexAccount indexAccount = (IndexAccount) cmd;
        try {
          indexAccountHandler.index(
              Account.id(indexAccount.getId()), Operation.INDEX, Optional.empty());
          log.atFine().log("Account index update on account %s done", indexAccount.getId());
        } catch (IOException e) {
          log.atSevere().withCause(e).log(
              "Account index update on account %s failed", indexAccount.getId());
          return false;
        }

      } else if (cmd instanceof EvictCache) {
        EvictCache evictCommand = (EvictCache) cmd;
        cacheEvictionHandler.evict(
            CacheEntry.from(evictCommand.getCacheName(), evictCommand.getKeyJson()));
        log.atFine().log(
            "Cache eviction %s %s done", evictCommand.getCacheName(), evictCommand.getKeyJson());

      } else if (cmd instanceof PostEvent) {
        Event event = ((PostEvent) cmd).getEvent();
        eventHandler.dispatch(event);
        log.atFine().log("Dispatching event %s done", event);
      } else if (cmd instanceof AddToProjectList) {
        String projectName = ((AddToProjectList) cmd).getProjectName();
        projectListUpdateHandler.update(projectName, false);

      } else if (cmd instanceof RemoveFromProjectList) {
        String projectName = ((RemoveFromProjectList) cmd).getProjectName();
        projectListUpdateHandler.update(projectName, true);
      }

      return true;
    } catch (Exception e) {
      return false;
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private Operation getOperation(IndexChange cmd) {
    if (cmd instanceof IndexChange.Update) {
      return Operation.INDEX;
    } else if (cmd instanceof IndexChange.Delete) {
      return Operation.DELETE;
    } else {
      throw new IllegalArgumentException("Unknown type of IndexChange command " + cmd.getClass());
    }
  }
}
