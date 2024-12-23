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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.util.concurrent.Executors;

@Singleton
public class ForwardedIndexFailsafeExecutorProvider implements Provider<FailsafeExecutor<Boolean>> {
  protected static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final Configuration cfg;

  @Inject
  public ForwardedIndexFailsafeExecutorProvider(Configuration cfg) {
    this.cfg = cfg;
  }

  @Override
  public FailsafeExecutor<Boolean> get() {
    RetryPolicy<Boolean> retryPolicy =
        RetryPolicy.<Boolean>builder()
            .withMaxAttempts(cfg.index().maxTries())
            .withDelay(cfg.index().retryInterval())
            .onRetry(e -> log.atFine().log("Retrying event %s", e))
            .onRetriesExceeded(
                e ->
                    log.atWarning().log(
                        "%d index retries exceeded for event %s", cfg.index().maxTries(), e))
            .handleResult(false)
            .abortOn(IOException.class)
            .build();
    // TODO: the executor shall be created by workQueue.createQueue(...)
    return Failsafe.with(retryPolicy).with(Executors.newScheduledThreadPool(threadPoolSize(cfg)));
  }

  protected int threadPoolSize(Configuration cfg) {
    return cfg.index().threadPoolSize();
  }
}
