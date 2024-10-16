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

import com.ericsson.gerrit.plugins.highavailability.SharedDirectory;
import com.ericsson.gerrit.plugins.highavailability.lock.FileBasedLock.Factory;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.LockManager;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;

public class FileBasedLockManager implements LockManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicItem.bind(binder(), LockManager.class).to(FileBasedLockManager.class);
      bind(TouchFileService.class).to(TouchFileServiceImpl.class);
      install(
          new FactoryModule() {
            @Override
            protected void configure() {
              factory(FileBasedLock.Factory.class);
            }
          });
      listener().to(StaleLockRemoval.class);
    }

    @Provides
    @Singleton
    @TouchFileExecutor
    ScheduledExecutorService createTouchFileExecutor(WorkQueue workQueue) {
      return workQueue.createQueue(2, "TouchFileService");
    }

    @Provides
    @Singleton
    @StaleLockRemovalExecutor
    ScheduledExecutorService createStaleLockRemovalExecutor(WorkQueue workQueue) {
      return workQueue.createQueue(1, "StaleLockRemoval");
    }

    @Provides
    @Singleton
    @LocksDirectory
    Path getLocksDirectory(@SharedDirectory Path sharedDir) throws IOException {
      Path locksDirPath = sharedDir.resolve("locks");
      Files.createDirectories(locksDirPath);
      return locksDirPath;
    }

    @Provides
    @Singleton
    @TouchFileInterval
    Duration getTouchFileInterval() {
      return Duration.ofSeconds(1);
    }

    @Provides
    @Singleton
    @StalenessCheckInterval
    Duration getStalenessCheckInterval() {
      return Duration.ofSeconds(2);
    }

    @Provides
    @Singleton
    @StalenessAge
    Duration getStalenessAge() {
      return Duration.ofSeconds(60);
    }
  }

  private final Factory lockFactory;

  @Inject
  FileBasedLockManager(FileBasedLock.Factory lockFactory) {
    this.lockFactory = lockFactory;
  }

  @Override
  public Lock getLock(String name) {
    logger.atInfo().log("FileBasedLockManager.lock(%s)", name);
    return lockFactory.create(name);
  }
}
