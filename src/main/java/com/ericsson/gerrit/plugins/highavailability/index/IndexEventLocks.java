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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class IndexEventLocks {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private static final int NUMBER_OF_INDEX_TASK_TYPES = 4;

  private final Striped<Semaphore> semaphores;
  private final long waitTimeout;

  @Inject
  public IndexEventLocks(Configuration cfg) {
    this.semaphores =
        Striped.semaphore(NUMBER_OF_INDEX_TASK_TYPES * cfg.index().numStripedLocks(), 1);
    this.waitTimeout = cfg.index().waitTimeout();
  }

  public void withLock(
      IndexTask id, IndexCallFunction function, VoidFunction lockAcquireTimeoutCallback) {
    Semaphore idLock = getSemaphore(id);
    try {
      log.atFine().log("Trying to acquire %s", id);
      if (idLock.tryAcquire(waitTimeout, TimeUnit.MILLISECONDS)) {
        log.atFine().log("Acquired %s", id);
        function
            .invoke()
            .whenComplete(
                (result, error) -> {
                  try {
                    log.atFine().log("Trying to release %s", id);
                    idLock.release();
                    log.atFine().log("Released %s", id);
                  } catch (Throwable t) {
                    log.atSevere().withCause(t).log("Unable to release %s", id);
                    throw t;
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
  protected Semaphore getSemaphore(IndexTask id) {
    return semaphores.get(id);
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
