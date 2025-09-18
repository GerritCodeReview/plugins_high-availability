// Copyright (C) 2023 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder.Result;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;

public class FailsafeExecutorProvider implements Provider<FailsafeExecutor<Result>> {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final Configuration cfg;
  private final WorkQueue workQueue;

  @Inject
  FailsafeExecutorProvider(Configuration cfg, WorkQueue workQueue) {
    this.cfg = cfg;
    this.workQueue = workQueue;
  }

  @Override
  public FailsafeExecutor<Result> get() {
    RetryPolicy<Result> retryPolicy =
        RetryPolicy.<Result>builder()
            .withMaxAttempts(cfg.jgroups().maxTries())
            .withDelay(cfg.jgroups().retryInterval())
            .onRetry(e -> log.atFine().log("Retrying event %s", e))
            .onRetriesExceeded(
                e ->
                    log.atWarning().log(
                        "%d jgroups retries exceeded for event %s", cfg.jgroups().maxTries(), e))
            .handleResultIf(r -> !r.getResult())
            .build();
    return Failsafe.with(retryPolicy)
        .with(workQueue.createQueue(cfg.jgroups().threadPoolSize(), "JGroupsForwarder"));
  }
}
