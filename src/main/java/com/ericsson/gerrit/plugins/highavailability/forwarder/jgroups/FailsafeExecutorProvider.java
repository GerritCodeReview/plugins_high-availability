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
import com.google.inject.Inject;
import com.google.inject.Provider;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import java.util.concurrent.Executors;

public class FailsafeExecutorProvider implements Provider<FailsafeExecutor<Boolean>> {

  private final Configuration cfg;

  @Inject
  FailsafeExecutorProvider(Configuration cfg) {
    this.cfg = cfg;
  }

  @Override
  public FailsafeExecutor<Boolean> get() {
    RetryPolicy<Boolean> retryPolicy =
        RetryPolicy.<Boolean>builder()
            .withMaxAttempts(cfg.jgroups().maxTries())
            .withDelay(cfg.jgroups().retryInterval())
            .handleResult(false)
            .build();
    return Failsafe.with(retryPolicy)
        .with(Executors.newScheduledThreadPool(cfg.jgroups().threadPoolSize()));
  }
}
