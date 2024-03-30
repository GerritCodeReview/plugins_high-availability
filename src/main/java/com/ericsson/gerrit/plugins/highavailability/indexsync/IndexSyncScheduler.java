// Copyright (C) 2024 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.indexsync;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import dev.failsafe.event.ExecutionCompletedEvent;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class IndexSyncScheduler implements LifecycleListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final WorkQueue workQueue;
  private final IndexSyncRunner.Factory indexSyncRunnerFactory;
  private final Configuration.IndexSync indexSync;

  private ScheduledExecutorService executor;

  @Inject
  IndexSyncScheduler(
      WorkQueue workQueue, IndexSyncRunner.Factory indexSyncRunnerFactory, Configuration cfg) {
    this.workQueue = workQueue;
    this.indexSyncRunnerFactory = indexSyncRunnerFactory;
    this.indexSync = cfg.indexSync();
  }

  @Override
  public void start() {
    executor = workQueue.createQueue(4, "IndexSyncRunner");

    scheduleInitialSync();
    schedulePeriodicSync();
  }

  private void scheduleInitialSync() {
    // Initial sync has to be run once but we may need to retry it until the other
    // peer becomes reachable
    // Therefore, we use failsafe to define and execute retries
    RetryPolicy<Boolean> retryPolicy =
        RetryPolicy.<Boolean>builder()
            .withMaxAttempts(12 * 60) // 5s * 12 * 60 = 1 hour
            .withDelay(Duration.ofSeconds(5))
            .onRetriesExceeded(e -> logRetriesExceeded(e))
            .handleResult(false)
            .build();
    FailsafeExecutor<Boolean> failsafeExecutor = Failsafe.with(retryPolicy).with(executor);

    IndexSyncRunner sync = indexSyncRunnerFactory.create(indexSync.initialSyncAge());
    failsafeExecutor.getAsync(sync);
  }

  private void schedulePeriodicSync() {
    // Periodic sync runs at fixed rate and we don't need failsafe for retries
    IndexSyncRunner sync = indexSyncRunnerFactory.create(indexSync.syncAge());
    executor.scheduleAtFixedRate(
        () -> sync.get(),
        indexSync.delay().getSeconds(),
        indexSync.period().getSeconds(),
        TimeUnit.SECONDS);
  }

  private void logRetriesExceeded(ExecutionCompletedEvent<Boolean> e) {
    log.atSevere().log("Retries for initial index sync exceeded %s", e);
  }

  @Override
  public void stop() {
    executor.shutdown();
  }
}
