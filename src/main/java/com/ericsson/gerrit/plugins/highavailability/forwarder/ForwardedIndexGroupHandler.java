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
import com.ericsson.gerrit.plugins.highavailability.index.ForwardedIndexExecutor;
import com.ericsson.gerrit.plugins.highavailability.index.GroupChecker;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Index a group using {@link GroupIndexer}. This class is meant to be used on the receiving side of
 * the {@link Forwarder} since it will prevent indexed group to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same group uuid
 */
@Singleton
public class ForwardedIndexGroupHandler extends ForwardedIndexingHandler<AccountGroup.UUID> {
  private final GroupIndexer indexer;
  private final GroupChecker groupChecker;
  private final ScheduledExecutorService indexExecutor;
  private final int retryInterval;
  private final int maxTries;

  @Inject
  ForwardedIndexGroupHandler(
      GroupIndexer indexer,
      GroupChecker groupChecker,
      Configuration config,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor) {
    this.indexer = indexer;
    this.groupChecker = groupChecker;
    this.indexExecutor = indexExecutor;
    Configuration.Index indexConfig = config.index();
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : 0;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
  }

  @Override
  protected void doIndex(AccountGroup.UUID uuid, Optional<IndexEvent> indexEvent) {
    doIndex(uuid, indexEvent, 0);
  }

  protected void doIndex(
      AccountGroup.UUID uuid, Optional<IndexEvent> groupIndexEvent, int retryCount) {
    indexer.index(uuid);
    if (groupChecker.isGroupUpToDate(uuid, groupIndexEvent)) {
      if (retryCount > 0) {
        log.atWarning().log(
            "Group '%s' has been eventually indexed after %s attempt(s)", uuid, retryCount);
      } else {
        log.atFine().log("Group '%s' successfully indexed", uuid);
      }
    } else {
      log.atFine().log("Group '%s' rescheduling indexing", uuid);
      rescheduleIndex(uuid, groupIndexEvent, retryCount + 1);
    }
  }

  private boolean rescheduleIndex(
      AccountGroup.UUID uuid, Optional<IndexEvent> groupIndexEvent, int retryCount) {
    if (retryCount > maxTries) {
      log.atSevere().log(
          "Group '%s' could not be indexed after %s retries. Group index could be stale.",
          uuid, retryCount);
      return false;
    }

    log.atWarning().log(
        "Retrying for the #%s time to index Group %s after %s msecs",
        retryCount, uuid, retryInterval);
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        indexExecutor.schedule(
            () -> {
              try {
                Context.setForwardedEvent(true);
                doIndex(uuid, groupIndexEvent, retryCount);
              } catch (Exception e) {
                log.atWarning().withCause(e).log("Group %s could not be indexed", uuid);
              }
            },
            retryInterval,
            TimeUnit.MILLISECONDS);
    return true;
  }

  @Override
  protected void doDelete(AccountGroup.UUID uuid, Optional<IndexEvent> indexEvent) {
    throw new UnsupportedOperationException("Delete from group index not supported");
  }
}
