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
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class RestForwarderScheduler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final ExecutorService executor;
  private final ScheduledExecutorService retryExecutor;
  private final long retryIntervalMs;

  public class CompletablePromise<V> extends CompletableFuture<V> {
    private Future<V> future;

    public CompletablePromise(Future<V> future) {
      this.future = future;
      executor.execute(this::tryToComplete);
    }

    private void tryToComplete() {
      if (future.isDone()) {
        try {
          complete(future.get());
        } catch (InterruptedException e) {
          completeExceptionally(e);
        } catch (ExecutionException e) {
          completeExceptionally(e.getCause());
        }
        return;
      }

      if (future.isCancelled()) {
        cancel(true);
        return;
      }

      executor.execute(this::tryToComplete);
    }
  }

  @Inject
  public RestForwarderScheduler(
      WorkQueue workQueue, Configuration cfg, Provider<Set<PeerInfo>> peerInfoProvider) {
    int executorSize = peerInfoProvider.get().size() * cfg.index().threadPoolSize();
    retryIntervalMs = cfg.index().retryInterval();
    this.retryExecutor = workQueue.createQueue(executorSize, "RestForwarderScheduler", false);
    this.executor = retryExecutor;
  }

  @VisibleForTesting
  public RestForwarderScheduler(ExecutorService executor, ScheduledExecutorService retryExecutor) {
    this.executor = executor;
    retryIntervalMs = 0;
    this.retryExecutor = retryExecutor;
  }

  public CompletableFuture<Boolean> execute(RestForwarder.Request request) {
    return flatten(
        CompletableFuture.supplyAsync(
            () -> {
              try {
                if (!request.execute()) {
                  if (retryExecutor != null) {
                    log.atWarning().log(
                        "Rescheduling %s for retry after %d msec", request, retryIntervalMs);
                    ScheduledFuture<CompletableFuture<Boolean>> requestFuture =
                        retryExecutor.schedule(
                            () -> RestForwarderScheduler.this.execute(request),
                            retryIntervalMs,
                            TimeUnit.MILLISECONDS);
                    return flatten(new CompletablePromise<>(requestFuture));
                  }
                  return CompletableFuture.completedFuture(false);
                }

                return CompletableFuture.completedFuture(true);
              } catch (ForwardingException e) {
                log.atSevere().withCause(e).log("Forwarding of %s has failed", request);
                return CompletableFuture.completedFuture(false);
              }
            },
            executor));
  }

  private <T> CompletableFuture<T> flatten(CompletableFuture<CompletableFuture<T>> nestedFuture) {
    return nestedFuture.thenCompose(Function.identity());
  }
}
