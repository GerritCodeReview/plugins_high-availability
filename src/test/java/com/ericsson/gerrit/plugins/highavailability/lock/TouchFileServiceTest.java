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
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TouchFileServiceTest {
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private ScheduledThreadPoolExecutor executor;
  private TouchFileService service;
  private Path locksDir;
  private Duration touchFileInterval;

  @Before
  public void setUp() throws IOException {
    executor = new ScheduledThreadPoolExecutor(2);
    touchFileInterval = Duration.ofSeconds(1);
    service = new TouchFileServiceImpl(executor, touchFileInterval);
    locksDir = tempFolder.newFolder().toPath();
  }

  @After
  public void tearDown() throws InterruptedException {
    executor.shutdown();
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  @Test
  public void touchServiceIncreasesLastModified() throws Exception {
    File f = Files.createFile(locksDir.resolve("foo")).toFile();
    service.touchForever(f);
    verifyLastUpdatedIncreases(f);
  }

  @Test
  public void touchTaskCancelation() throws Exception {
    File f = Files.createFile(locksDir.resolve("foo")).toFile();
    ScheduledFuture<?> touchTask = service.touchForever(f);
    touchTask.cancel(false);
    verifyLastUpdatedDoesNotIncrease(f);
  }

  @Test
  public void touchTaskStopsWhenFileDisappears() throws Exception {
    File f = Files.createFile(locksDir.resolve("foo")).toFile();
    ScheduledFuture<?> touchTask = service.touchForever(f);
    Thread.sleep(touchFileInterval.toMillis());

    assertThat(touchTask.isDone()).isFalse();

    assertThat(f.delete()).isTrue();
    Thread.sleep(touchFileInterval.toMillis());

    assertThat(touchTask.isDone()).isTrue();
    try {
      touchTask.get();
      Assert.fail("Expected an exception from touchTask.get()");
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
      RuntimeException cause = (RuntimeException) e.getCause();
      assertThat(cause.getMessage()).contains("stopping");
    }
  }

  private void verifyLastUpdatedIncreases(File f) throws InterruptedException {
    long start = f.lastModified();
    long previous = start;
    long last = start;
    for (int i = 0; i < 3; i++) {
      Thread.sleep(1000);
      long current = f.lastModified();
      assertThat(current).isAtLeast(previous);
      last = current;
    }
    assertThat(last).isGreaterThan(start);
  }

  private void verifyLastUpdatedDoesNotIncrease(File f) throws InterruptedException {
    long start = f.lastModified();
    for (int i = 0; i < 3; i++) {
      Thread.sleep(1000);
      long current = f.lastModified();
      assertThat(current).isEqualTo(start);
    }
  }
}
