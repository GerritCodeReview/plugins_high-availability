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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import java.util.concurrent.Executors;

@Singleton
public class FailsafeExecutorProvider implements Provider<FailsafeExecutor<Boolean>> {

  private final Configuration cfg;

  @Inject
  FailsafeExecutorProvider(Configuration cfg) {
    this.cfg = cfg;
  }

  @Override
  public FailsafeExecutor<Boolean> get() {
    Fallback<Boolean> fallbackToFalse = Fallback.<Boolean>of(() -> false);
    // TODO: add listeners and logs
    // https://failsafe.dev/retry/#event-listeners
    RetryPolicy<Boolean> retryPolicy =
        RetryPolicy.<Boolean>builder()
            .withMaxAttempts(cfg.http().maxTries())
            .withDelay(cfg.http().retryInterval())
            .handleResult(false)
            .abortIf(
                (r, e) ->
                    e instanceof ForwardingException && !((ForwardingException) e).isRecoverable())
            .build();
    // TODO: the executor shall be created by workQueue.createQueue(...)
    //   However, this currently doesn't work because WorkQueue.Executor doesn't support wrapping of
    //   Callable i.e. it throws an exception on decorateTask(Callable)
    return Failsafe.with(fallbackToFalse, retryPolicy)
        .with(Executors.newScheduledThreadPool(cfg.http().threadPoolSize()));
  }
}
