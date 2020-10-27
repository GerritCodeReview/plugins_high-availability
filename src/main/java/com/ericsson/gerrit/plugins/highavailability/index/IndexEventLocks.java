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
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.DeleteChangeTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexAccountTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexChangeTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexGroupTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexProjectTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexTask;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Striped;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class IndexEventLocks {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final Striped<Lock> accountIdLocks;
  private final Striped<Lock> changeIdLocks;
  private final Striped<Lock> projectIdLocks;
  private final Striped<Lock> groupIdLocks;

  private final long waitTimeout;

  @Inject
  public IndexEventLocks(Configuration cfg) {
    this.accountIdLocks = Striped.lock(cfg.index().numStripedLocks());
    this.changeIdLocks = Striped.lock(cfg.index().numStripedLocks());
    this.projectIdLocks = Striped.lock(cfg.index().numStripedLocks());
    this.groupIdLocks = Striped.lock(cfg.index().numStripedLocks());
    this.waitTimeout = cfg.index().waitTimeout();
  }

  public void withLock(
      IndexTask id, VoidFunction function, VoidFunction lockAcquireTimeoutCallback) {
    Lock idLock = getLock(id);
    try {
      if (idLock.tryLock(waitTimeout, TimeUnit.MILLISECONDS)) {
        try {
          function.invoke();
        } finally {
          idLock.unlock();
        }
      } else {
        lockAcquireTimeoutCallback.invoke();
      }
    } catch (InterruptedException e) {
      log.atSevere().withCause(e).log("%s was interrupted; giving up", id);
    }
  }

  protected Lock getLock(IndexTask task) {
    if (task instanceof IndexChangeTask || task instanceof DeleteChangeTask) {
      return changeIdLocks.get(task);
    }

    if (task instanceof IndexAccountTask) {
      return accountIdLocks.get(task);
    }

    if (task instanceof IndexGroupTask) {
      return groupIdLocks.get(task);
    }

    if (task instanceof IndexProjectTask) {
      return projectIdLocks.get(task);
    }

    throw new IllegalStateException(
        String.format("Unknown index task type:%s", task.getClass().getName()));
  }

  @FunctionalInterface
  public interface VoidFunction {
    void invoke();
  }
}
