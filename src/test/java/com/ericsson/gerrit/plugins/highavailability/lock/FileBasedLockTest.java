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
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_SUPPORTSATOMICFILECREATION;

import com.google.gerrit.server.util.git.DelegateSystemReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FileBasedLockTest {

  private static SystemReader setFakeSystemReader(FileBasedConfig cfg) {
    SystemReader oldSystemReader = SystemReader.getInstance();
    SystemReader.setInstance(
        new DelegateSystemReader(oldSystemReader) {
          @Override
          public FileBasedConfig openUserConfig(Config parent, FS fs) {
            return cfg;
          }
        });
    return oldSystemReader;
  }

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private final Duration touchInterval = Duration.ofSeconds(1);
  private final String lockName = "mylock";

  private ScheduledExecutorService touchFileExecutor;
  private TouchFileService touchFileService;
  private FileBasedLock lock;

  private FileBasedConfig cfg;

  @Parameter
  public boolean supportsAtomicFileCreation;

  @Parameters(name = "supportsAtomicFileCreation={0}")
  public static Boolean[] testData() {
    return new Boolean[] {true, false};
  }

  SystemReader oldSystemReader;

  @Before
  public void setUp() throws IOException {
    touchFileExecutor = Executors.newScheduledThreadPool(2);
    touchFileService = new TouchFileServiceImpl(touchFileExecutor, touchInterval);
    Path locksDir = Path.of(tempFolder.newFolder().getPath());
    lock = new FileBasedLock(locksDir, touchFileService, lockName);

    File cfgFile = tempFolder.newFile(".gitconfig");
    cfg = new FileBasedConfig(cfgFile, FS.DETECTED);
    cfg.setBoolean(
        CONFIG_CORE_SECTION,
        null,
        CONFIG_KEY_SUPPORTSATOMICFILECREATION,
        supportsAtomicFileCreation);
    cfg.save();
    oldSystemReader = setFakeSystemReader(cfg);
  }

  @After
  public void tearDown() throws Exception {
    touchFileExecutor.shutdown();
    touchFileExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    SystemReader.setInstance(oldSystemReader);
  }

  @Test
  public void lockCreatesFile_unlockDeletesFile() {
    Path lockPath = lock.getLockPath();

    assertThat(Files.exists(lockPath)).isFalse();

    lock.lock();
    assertThat(Files.exists(lockPath)).isTrue();

    lock.unlock();
    assertThat(Files.exists(lockPath)).isFalse();
  }

  @Test
  public void testLockFileNameAndContent() throws IOException {
    lock.lock();
    Path lockPath = lock.getLockPath();

    String content = Files.readString(lockPath, StandardCharsets.UTF_8);
    assertThat(content).endsWith("\n");
    LockFileFormat lockFileFormat = new LockFileFormat(content.substring(0, content.length() - 1));
    assertThat(content).isEqualTo(lockFileFormat.content());
    assertThat(lockPath.getFileName().toString()).isEqualTo(lockFileFormat.fileName());
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

    Duration timeout = Duration.ofSeconds(1);
    Future<Boolean> r =
        executor.submit(() -> lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS));

    Thread.sleep(timeout.dividedBy(2).toMillis());

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
      Thread.sleep(touchInterval.toMillis());
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
