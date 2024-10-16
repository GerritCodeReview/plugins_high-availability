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

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedLockTest {
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private ScheduledExecutorService touchFileExecutor;
  private TouchFileService touchFileService;
  private Path lockPath;
  private FileBasedLock lock;

  @Before
  public void setUp() throws IOException {
    touchFileExecutor = Executors.newScheduledThreadPool(2);
    touchFileService = new TouchFileServiceImpl(touchFileExecutor);
    lockPath = Path.of(tempFolder.newFolder().getPath(), "mylock");
    lock = new FileBasedLock(lockPath, "content", touchFileService);
  }

  @After
  public void tearDown() throws Exception {
    touchFileExecutor.shutdown();
    touchFileExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  @Test
  public void lockCreatesFile_unlockDeletesFile() {
    assertThat(Files.exists(lockPath)).isFalse();

    lock.lock();
    assertThat(Files.exists(lockPath)).isTrue();

    lock.unlock();
    assertThat(Files.exists(lockPath)).isFalse();
  }

  @Test
  public void tryLockAfterLock_fail() {
    lock.lock();
    assertThat(lock.tryLock()).isFalse();
  }

  @Test
  public void tryLockWithTimeout_failsAfterTimeout() throws InterruptedException {
    lock.lock();
    assertThat(lock.tryLock(1, TimeUnit.SECONDS)).isFalse();
  }

  @Test
  public void concurrentTryLock_exactlyOneSucceeds()
      throws InterruptedException, ExecutionException {
    ExecutorService executor = Executors.newFixedThreadPool(2);

    for (int i = 0; i < 10; i++) {
      Future<Boolean> r1 = executor.submit(() -> lock.tryLock());
      Future<Boolean> r2 = executor.submit(() -> lock.tryLock());
      assertThat(r1.get()).isNotEqualTo(r2.get());
      lock.unlock();
    }

    executor.shutdown();
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  @Test
  public void tryLockWithTimeout_succeedsIfLockReleased() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(1);

    lock.lock();
    Future<Boolean> r = executor.submit(() -> lock.tryLock(1, TimeUnit.SECONDS));

    Thread.sleep(500);
    lock.unlock();

    assertThat(r.get()).isTrue();

    executor.shutdown();
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  @Test
  public void tryLockWithTimeout_failsIfLockNotReleased() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(1);

    lock.lock();
    Future<Boolean> r = executor.submit(() -> lock.tryLock(1, TimeUnit.SECONDS));

    assertThat(r.get()).isFalse();

    executor.shutdown();
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  @Test
  public void liveLock_lastUpdatedKeepsIncreasing() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    CountDownLatch lockAcquired = new CountDownLatch(1);
    CountDownLatch testDone = new CountDownLatch(1);

    executor.submit(() -> acquireAndReleaseLock(lockAcquired, testDone));
    lockAcquired.await();

    File lockFile = lock.getLockPath().toFile();
    long start = lockFile.lastModified();
    long previous = start;
    long last = start;
    for (int i = 0; i < 3; i++) {
      Thread.sleep(1000);
      long current = lockFile.lastModified();
      assertThat(current).isAtLeast(previous);
      last = current;
    }
    assertThat(last).isGreaterThan(start);

    testDone.countDown();

    executor.shutdown();
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  private void acquireAndReleaseLock(CountDownLatch lockAcquired, CountDownLatch testDone) {
    lock.lock();
    lockAcquired.countDown();
    try {
      testDone.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      lock.unlock();
    }
  }
}
