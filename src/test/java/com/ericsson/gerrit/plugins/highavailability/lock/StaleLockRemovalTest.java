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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class StaleLockRemovalTest {
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private ScheduledExecutorService executor;
  private StaleLockRemoval staleLockRemoval;
  private Path locksDir;
  private Duration stalenessAge;

  @Before
  public void setUp() throws IOException {
    executor = Mockito.mock(ScheduledExecutorService.class);
    executor = new ScheduledThreadPoolExecutor(2);
    locksDir = tempFolder.newFolder().toPath();
    stalenessAge = Duration.ofSeconds(3);
    staleLockRemoval =
        new StaleLockRemoval(executor, Duration.ofSeconds(1), stalenessAge, locksDir);
  }

  @Test
  public void staleLockRemoved() throws Exception {
    Path lockPath = createLockFile("foo");
    Thread.sleep(stalenessAge.toMillis());
    assertFilesExist(lockPath);
    staleLockRemoval.run();
    assertFilesDoNotExist(lockPath);
  }

  @Test
  public void nonStaleLockNotRemoved() throws Exception {
    Path lockPath = createLockFile("foo");
    staleLockRemoval.run();
    assertFilesExist(lockPath);
  }

  @Test
  public void nonLockFilesNotRemoved() throws Exception {
    Path nonLock = Files.createFile(locksDir.resolve("nonLock"));
    Thread.sleep(stalenessAge.toMillis());
    staleLockRemoval.run();
    assertFilesExist(nonLock);
  }

  @Test
  public void multipleLocksHandledProperly() throws Exception {
    Path stale1 = createLockFile("stale-1");
    Path stale2 = createLockFile("stale-2");
    Path stale3 = createLockFile("stale-3");

    Thread.sleep(stalenessAge.toMillis());

    Path live1 = createLockFile("live-1");
    Path live2 = createLockFile("live-2");
    Path live3 = createLockFile("live-3");

    staleLockRemoval.run();
    assertFilesDoNotExist(stale1, stale2, stale3);
    assertFilesExist(live1, live2, live3);
  }

  private Path createLockFile(String name) throws IOException {
    return Files.createFile(locksDir.resolve(new LockFileFormat(name).fileName()));
  }

  private void assertFilesExist(Path... paths) {
    for (Path p : paths) {
      assertThat(Files.exists(p)).isTrue();
    }
  }

  private void assertFilesDoNotExist(Path... paths) {
    for (Path p : paths) {
      assertThat(Files.exists(p)).isFalse();
    }
  }
}
