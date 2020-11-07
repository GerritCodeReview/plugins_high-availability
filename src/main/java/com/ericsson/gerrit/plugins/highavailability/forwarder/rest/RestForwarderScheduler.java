// Copyright (C) 2015 The Android Open Source Project
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
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RestForwarderScheduler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final Executor executor;
  private final ScheduledThreadPoolExecutor retryExecutor;
  private final long retryIntervalMs;

  @Inject
  public RestForwarderScheduler(
      WorkQueue workQueue, Configuration cfg, Provider<Set<PeerInfo>> peerInfoProvider) {
    int executorSize = peerInfoProvider.get().size() * cfg.index().threadPoolSize();
    retryIntervalMs = cfg.index().retryInterval();
    this.retryExecutor = workQueue.createQueue(executorSize, "RestForwarderScheduler", false);
    this.executor = retryExecutor;
  }

  @VisibleForTesting
  public RestForwarderScheduler() {
    executor = MoreExecutors.directExecutor();
    retryIntervalMs = 0;
    retryExecutor = null;
  }

  public void execute(RestForwarder.Request request) {
    executor.execute(
        () -> {
          try {
            if (!request.execute() && retryExecutor != null) {
              log.atWarning().log(
                  "Rescheduling %s for retry after %d msec", request, retryIntervalMs);
              retryExecutor.schedule(
                  () -> RestForwarderScheduler.this.execute(request),
                  retryIntervalMs,
                  TimeUnit.MILLISECONDS);
            }
          } catch (ForwardingException e) {
            log.atSevere().withCause(e).log("Forwarding of %s has failed", request);
          }
        });
  }
}
