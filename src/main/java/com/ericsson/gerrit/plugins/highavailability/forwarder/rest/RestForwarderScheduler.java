// Copyright (C) 2020 The Android Open Source Project
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
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Singleton
public class RestForwarderScheduler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final ScheduledExecutorService executor;
  private final Duration retryInterval;

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
    retryInterval = cfg.index().retryInterval();
    this.executor = workQueue.createQueue(executorSize, "RestForwarderScheduler");
  }

  @VisibleForTesting
  public RestForwarderScheduler(ScheduledExecutorService executor) {
    this.executor = executor;
    retryInterval = Duration.ZERO;
  }

  public CompletableFuture<Boolean> execute(RestForwarder.Request request) {
    return execute(request, Duration.ZERO);
  }

  public CompletableFuture<Boolean> execute(RestForwarder.Request request, Duration delay) {
    return supplyAsync(
        request.toString(),
        () -> {
          try {
            if (!request.execute()) {
              log.atWarning().log("Rescheduling %s for retry after %s", request, retryInterval);
              return execute(request, retryInterval);
            }
            return CompletableFuture.completedFuture(true);
          } catch (ForwardingException e) {
            log.atSevere().withCause(e).log("Forwarding of %s has failed", request);
            return CompletableFuture.completedFuture(false);
          }
        },
        executor,
        delay);
  }

  private CompletableFuture<Boolean> supplyAsync(
      String taskName,
      Supplier<CompletableFuture<Boolean>> fn,
      ScheduledExecutorService executor,
      Duration delay) {
    BooleanAsyncSupplier asyncSupplier = new BooleanAsyncSupplier(taskName, fn);
    executor.schedule(asyncSupplier, delay.toMillis(), TimeUnit.MILLISECONDS);
    return asyncSupplier.future();
  }

  static class BooleanAsyncSupplier implements Runnable {
    private CompletableFuture<CompletableFuture<Boolean>> dep;
    private Supplier<CompletableFuture<Boolean>> fn;
    private String taskName;

    BooleanAsyncSupplier(String taskName, Supplier<CompletableFuture<Boolean>> fn) {
      this.taskName = taskName;
      this.dep = new CompletableFuture<>();
      this.fn = fn;
    }

    public CompletableFuture<Boolean> future() {
      return dep.thenCompose(Function.identity());
    }

    @Override
    public void run() {
      try {
        dep.complete(fn.get());
      } catch (Throwable ex) {
        dep.completeExceptionally(ex);
      }
    }

    @Override
    public String toString() {
      return taskName;
    }
  }
}
