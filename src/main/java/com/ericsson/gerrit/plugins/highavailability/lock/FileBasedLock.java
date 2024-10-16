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
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.LockToken;

public class FileBasedLock implements Lock {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Path lockPath;
  private final String content;
  private final TouchFileService touchFileService;

  private volatile ScheduledFuture<?> touchTask;
  private volatile LockToken lockToken;

  public FileBasedLock(Path lockPath, String content, TouchFileService touchFileService) {
    this.lockPath = lockPath;
    this.content = content;
    this.touchFileService = touchFileService;
  }

  @Override
  public void lock() {
    try {
      tryLock(Long.MAX_VALUE, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.atSevere().withCause(e).log("Interrupted while trying to lock: %s", lockPath);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void lockInterruptibly() throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean tryLock() {
    try {
      createLockFile();
      touchTask = touchFileService.touchForever(lockPath.toFile());
      return true;
    } catch (IOException e) {
      logger.atInfo().withCause(e).log("Couldn't create lock file: %s", lockPath);
      return false;
    }
  }

  @Override
  public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
    RetryPolicy<Object> retry =
        RetryPolicy.builder()
            .withMaxAttempts(-1)
            .withBackoff(10, 1000, ChronoUnit.MILLIS)
            .withMaxDuration(Duration.of(time, unit.toChronoUnit()))
            .handleResult(false)
            .build();
    return Failsafe.with(retry).get(this::tryLock);
  }

  public Path getLockPath() {
    return lockPath;
  }

  private Path createLockFile() throws IOException {
    File f = lockPath.toFile();
    lockToken = FS.DETECTED.createNewFileAtomic(f);
    if (!lockToken.isCreated()) {
      throw new IOException("Couldn't create " + lockPath);
    }
    f.deleteOnExit();
    return lockPath;
  }

  @Override
  public void unlock() {
    try {
      if (touchTask != null) {
        touchTask.cancel(false);
      }
      Files.deleteIfExists(lockPath);
      if (lockToken != null) {
        lockToken.close();
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Couldn't delete lock file: %s", lockPath);
      throw new RuntimeException(e);
    }
  }

  @Override
  public Condition newCondition() {
    throw new UnsupportedOperationException();
  }
}
