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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Singleton
public class TouchFileServiceImpl implements TouchFileService {
  static final Duration TOUCH_FILE_INTERVAL = Duration.ofSeconds(1);

  private final ScheduledExecutorService executor;

  @Inject
  public TouchFileServiceImpl(@TouchFileExecutor ScheduledExecutorService executor) {
    this.executor = executor;
  }

  @Override
  public ScheduledFuture<?> touchForever(File file) {
    return executor.scheduleAtFixedRate(
        () -> touch(file),
        TOUCH_FILE_INTERVAL.getSeconds(),
        TOUCH_FILE_INTERVAL.getSeconds(),
        TimeUnit.SECONDS);
  }

  private static void touch(File f) {
    boolean succeeded = f.setLastModified(System.currentTimeMillis());
    if (!succeeded) {
      if (!f.exists()) {
        throw new RuntimeException(
            String.format("File %s doesn't exist, stopping the touch task", f.toPath()));
      }
    }
  }
}
