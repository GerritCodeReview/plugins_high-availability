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

package com.ericsson.gerrit.plugins.highavailability;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AutoReindexListener implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(AutoReindexListener.class);
  private final Configuration.AutoReindex cfg;
  private final ChangeReindexRunnable changeReindex;
  private final AccountReindexRunnable accountReindex;
  private final GroupReindexRunnable groupReindex;
  private final WorkQueue.Executor exec;
  private final List<Future<?>> futureTasks = new ArrayList<>();

  @Inject
  public AutoReindexListener(
      Configuration cfg,
      WorkQueue workQueue,
      ChangeReindexRunnable changeReindex,
      AccountReindexRunnable accountReindex,
      GroupReindexRunnable groupReindex) {
    this.cfg = cfg.autoReindex();
    this.changeReindex = changeReindex;
    this.accountReindex = accountReindex;
    this.groupReindex = groupReindex;
    this.exec = workQueue.getDefaultQueue();
  }

  @Override
  public void start() {
    if (cfg.enabled()) {
      if (cfg.pollSec() > 0) {
        log.info("Scheduling auto-reindex after {}s and every {}s", cfg.delaySec(), cfg.pollSec());
        futureTasks.add(
            exec.scheduleAtFixedRate(
                changeReindex, cfg.delaySec(), cfg.pollSec(), TimeUnit.SECONDS));
        futureTasks.add(
            exec.scheduleAtFixedRate(
                accountReindex, cfg.delaySec(), cfg.pollSec(), TimeUnit.SECONDS));
        futureTasks.add(
            exec.scheduleAtFixedRate(
                groupReindex, cfg.delaySec(), cfg.pollSec(), TimeUnit.SECONDS));
      } else {
        log.info("Scheduling auto-reindex after {}s", cfg.delaySec());
        futureTasks.add(exec.schedule(changeReindex, cfg.delaySec(), TimeUnit.SECONDS));
        futureTasks.add(exec.schedule(accountReindex, cfg.delaySec(), TimeUnit.SECONDS));
        futureTasks.add(exec.schedule(groupReindex, cfg.delaySec(), TimeUnit.SECONDS));
      }
    }
  }

  @Override
  public void stop() {
    futureTasks.forEach(t -> t.cancel(true));
  }
}
