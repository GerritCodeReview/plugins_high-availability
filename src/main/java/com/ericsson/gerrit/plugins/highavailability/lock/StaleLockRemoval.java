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

package com.ericsson.gerrit.plugins.highavailability.lock;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class StaleLockRemoval implements LifecycleListener, Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ScheduledExecutorService executor;
  private final Duration checkInterval;
  private final Duration stalenessAge;
  private final Path locksDir;

  @Inject
  StaleLockRemoval(
      @StaleLockRemovalExecutor ScheduledExecutorService executor,
      @StalenessCheckInterval Duration checkInterval,
      @StalenessAge Duration stalenessAge,
      @LocksDirectory Path locksDir) {
    this.executor = executor;
    this.checkInterval = checkInterval;
    this.stalenessAge = stalenessAge;
    this.locksDir = locksDir;
  }

  @Override
  public void start() {
    logger.atFine().log(
        "Scheduling StaleLockRemoval to run every %d seconds", checkInterval.getSeconds());
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        executor.scheduleWithFixedDelay(
            this, checkInterval.getSeconds(), checkInterval.getSeconds(), TimeUnit.SECONDS);
    logger.atFine().log(
        "Scheduled StaleLockRemoval to run every %d seconds", checkInterval.getSeconds());
  }

  @Override
  public void run() {
    try (Stream<Path> stream = Files.walk(locksDir)) {
      stream.filter(Files::isRegularFile).forEach(lockPath -> removeIfStale(lockPath));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Error while performing stale lock detection and removal");
    }
  }

  private void removeIfStale(Path lockPath) {
    logger.atFine().log("Inspecting %s", lockPath);
    Instant now = Instant.now();
    Instant lastModified = Instant.ofEpochMilli(lockPath.toFile().lastModified());
    if (Duration.between(lastModified, now).compareTo(stalenessAge) > 0) {
      logger.atInfo().log("Detected stale lock %s", lockPath);
      try {
        if (Files.deleteIfExists(lockPath)) {
          logger.atInfo().log("Stale lock %s removed", lockPath);
        } else {
          logger.atInfo().log("Stale lock %s was removed by another thread", lockPath);
        }
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Couldn't delete stale lock %s", lockPath);
      }
    }
  }

  @Override
  public void stop() {}
}
