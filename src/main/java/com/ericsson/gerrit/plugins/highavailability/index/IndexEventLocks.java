// Copyright (C) 2020 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexTask;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Striped;
import com.google.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class IndexEventLocks {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private static final int NUMBER_OF_INDEX_TASK_TYPES = 4;

  private final Striped<Lock> locks;
  private final long waitTimeout;

  @Inject
  public IndexEventLocks(Configuration cfg) {
    this.locks = Striped.lock(NUMBER_OF_INDEX_TASK_TYPES * cfg.index().numStripedLocks());
    this.waitTimeout = cfg.index().waitTimeout();
  }

  public void withLock(
      IndexTask id, IndexCallFunction function, VoidFunction lockAcquireTimeoutCallback) {
    Lock idLock = getLock(id);
    try {
      log.atInfo().log("Trying to lock %s", id);
      if (idLock.tryLock(waitTimeout, TimeUnit.MILLISECONDS)) {
        log.atInfo().log("Locked %s", id);
        function
            .invoke()
            .whenComplete(
                (result, error) -> {
                  try {
                    log.atInfo().log("Trying to unlock %s", id);
                    idLock.unlock();
                    log.atInfo().log("Unlocked %s", id);
                  } catch (Throwable t) {
                    log.atSevere().withCause(t).log("Unable to unlock %s", id);
                  }
                });
      } else {
        lockAcquireTimeoutCallback.invoke();
      }
    } catch (InterruptedException e) {
      log.atSevere().withCause(e).log("%s was interrupted; giving up", id);
    }
  }

  @VisibleForTesting
  protected Lock getLock(IndexTask id) {
    return locks.get(id);
  }

  @FunctionalInterface
  public interface VoidFunction {
    void invoke();
  }

  @FunctionalInterface
  public interface IndexCallFunction {
    CompletableFuture<?> invoke();
  }
}
