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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.google.common.flogger.FluentLogger;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class to handle forwarded indexing. Prevents indexed entities from being forwarded again
 * (infinite loop) and ensures no concurrent indexing is done for the same id.
 */
public abstract class ForwardedIndexingHandler<T> {
  protected static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final Set<T> inFlightIndexing = Collections.newSetFromMap(new ConcurrentHashMap<>());

  protected CompletableFuture<Boolean> withInFlightGuard(
      T id, Callable<CompletableFuture<Boolean>> action) throws Exception {
    if (inFlightIndexing.add(id)) {
      try {
        CompletableFuture<Boolean> future = action.call();
        return future.whenComplete((r, t) -> inFlightIndexing.remove(id));
      } catch (Exception e) {
        inFlightIndexing.remove(id);
        throw e;
      }
    }
    throw new InFlightIndexedException(String.format("Indexing for %s already in flight", id));
  }

  protected static <V> V withForwardedEventFlag(Callable<V> action) throws Exception {
    Context.setForwardedEvent(true);
    try {
      return action.call();
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
