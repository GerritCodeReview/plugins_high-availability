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
import com.ericsson.gerrit.plugins.highavailability.forwarder.retry.IndexingRetry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.retry.IndexingRetryResult;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.function.CheckedPredicate;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Singleton
public class ForwardedIndexExecutorProvider {
  protected static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final Configuration cfg;

  @Inject
  public ForwardedIndexExecutorProvider(Configuration cfg) {
    this.cfg = cfg;
  }

  public FailsafeExecutor<IndexingRetryResult> get(Map<?, IndexingRetry> inFlightIndexingTasks) {
    CheckedPredicate<IndexingRetryResult> checker =
        (result) -> {
          if (result.isSuccessful()) {
            return false;
          }
          int retryNum = result.getRetry().incrementAndGetRetryNumber();
          boolean isMaxRetires = retryNum >= cfg.index().maxTries();
          if (isMaxRetires) {
            inFlightIndexingTasks.remove(result.getRetry().getId(), result.getRetry());
          }
          return isMaxRetires;
        };

    Consumer<ExecutionCompletedEvent<IndexingRetryResult>> removeIndexingTask =
        (event) -> {
          inFlightIndexingTasks.remove(
              event.getResult().getRetry().getId(), event.getResult().getRetry());
        };

    RetryPolicy<IndexingRetryResult> retryPolicy =
        RetryPolicy.<IndexingRetryResult>builder()
            // To squash multiple events for the same id we have to reset number of the retries
            // only way to do it is to handle it independently from Failsafe counter, that's why
            // max attempts is set to max value
            .withMaxAttempts(Integer.MAX_VALUE)
            .withDelay(cfg.index().retryInterval())
            .onRetry(e -> log.atWarning().log("Retrying event %s", e))
            .onRetriesExceeded(
                e -> {
                  log.atWarning().log(
                      "%d index retries exceeded for event %s", cfg.index().maxTries(), e);
                  removeIndexingTask.accept(e);
                })
            .onAbort(
                e -> {
                  log.atWarning().log("Event %s aborted", e);
                  removeIndexingTask.accept(e);
                })
            .onSuccess(
                e -> {
                  log.atFine().log("Indexing successful for event %s", e);
                  removeIndexingTask.accept(e);
                })
            .onFailure(
                e -> {
                  log.atWarning().log("Indexing failed for event %s", e);
                  removeIndexingTask.accept(e);
                })
            .handleResultIf(checker)
            .abortOn(IOException.class)
            .build();
    // TODO: the executor shall be created by workQueue.createQueue(...)
    return Failsafe.with(retryPolicy).with(Executors.newScheduledThreadPool(threadPoolSize(cfg)));
  }

  protected int threadPoolSize(Configuration cfg) {
    return cfg.index().threadPoolSize();
  }
}
