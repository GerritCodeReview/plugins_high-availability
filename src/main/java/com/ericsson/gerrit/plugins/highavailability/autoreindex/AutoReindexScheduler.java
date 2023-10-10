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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class AutoReindexScheduler implements LifecycleListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final Configuration.AutoReindex cfg;
  private final ChangeReindexRunnable changeReindex;
  private final AccountReindexRunnable accountReindex;
  private final GroupReindexRunnable groupReindex;
  private final ProjectReindexRunnable projectReindex;
  private final ScheduledExecutorService executor;
  private final List<Future<?>> futureTasks = new ArrayList<>();

  @Inject
  public AutoReindexScheduler(
      Configuration cfg,
      WorkQueue workQueue,
      ChangeReindexRunnable changeReindex,
      AccountReindexRunnable accountReindex,
      GroupReindexRunnable groupReindex,
      ProjectReindexRunnable projectReindex) {
    this.cfg = cfg.autoReindex();
    this.changeReindex = changeReindex;
    this.accountReindex = accountReindex;
    this.groupReindex = groupReindex;
    this.projectReindex = projectReindex;
    this.executor = workQueue.createQueue(1, "HighAvailability-AutoReindex");
  }

  @Override
  public void start() {
    if (cfg.pollInterval().compareTo(Duration.ZERO) > 0) {
      log.atInfo().log(
          "Scheduling auto-reindex after %s and every %s", cfg.delay(), cfg.pollInterval());
      for (Runnable reindexTask :
          List.of(changeReindex, accountReindex, groupReindex, projectReindex)) {
        futureTasks.add(
            executor.scheduleAtFixedRate(
                reindexTask,
                cfg.delay().toSeconds(),
                cfg.pollInterval().toSeconds(),
                TimeUnit.SECONDS));
      }
    } else {
      log.atInfo().log("Scheduling auto-reindex after %s", cfg.delay());
      for (Runnable reindexTask :
          List.of(changeReindex, accountReindex, groupReindex, projectReindex)) {
        futureTasks.add(executor.schedule(reindexTask, cfg.delay().toSeconds(), TimeUnit.SECONDS));
      }
    }
  }

  @Override
  public void stop() {
    futureTasks.forEach(t -> t.cancel(true));
    executor.shutdown();
  }
}
