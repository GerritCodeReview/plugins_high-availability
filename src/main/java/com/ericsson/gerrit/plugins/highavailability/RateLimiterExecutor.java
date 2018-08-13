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

import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Executor;
import java.util.Optional;

public class RateLimiterExecutor implements TaskExecutor {
  private final Executor executor;
  private final Optional<RateLimiter> rateLimiter;

  RateLimiterExecutor(WorkQueue.Executor executor, int maxRate) {
    this.executor = executor;
    this.rateLimiter = maxRate > 0 ? Optional.of(RateLimiter.create(maxRate)) : Optional.empty();
  }

  @Override
  public void shutdown() {
    executor.shutdown();
  }

  @Override
  public void unregisterWorkQueue() {
    executor.unregisterWorkQueue();
  }

  @Override
  public void execute(Runnable command) {
    rateLimiter.ifPresent(rate -> rate.acquire());
    executor.execute(command);
  }
}
