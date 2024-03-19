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

import com.ericsson.gerrit.plugins.highavailability.forwarder.retry.IndexingRetry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.retry.IndexingRetryResult;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class to handle forwarded indexing. This class is meant to be extended by classes used on
 * the receiving side of the {@link Forwarder} since it will prevent indexing to be forwarded again
 * causing an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent
 * indexing is done for the same id.
 */
public abstract class ForwardedIndexingHandler<T> {
  protected static final FluentLogger log = FluentLogger.forEnclosingClass();
  protected final Map<T, IndexingRetry> inFlightIndexing = new ConcurrentHashMap<>();

  public enum Operation {
    INDEX,
    DELETE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  protected abstract CompletableFuture<IndexingRetryResult> doIndex(T id) throws IOException;

  protected abstract CompletableFuture<IndexingRetryResult> doDelete(
      T id, Optional<IndexEvent> indexEvent) throws IOException;

  protected abstract boolean indexOnce(T id) throws Exception;

  /**
   * Index an item in the local node, indexing will not be forwarded to the other node.
   *
   * @param id The id to index.
   * @param operation The operation to do; index or delete
   * @param indexEvent The index event details.
   * @throws IOException If an error occur while indexing.
   */
  public CompletableFuture<IndexingRetryResult> index(
      T id, Operation operation, Optional<IndexEvent> indexEvent) throws IOException {
    log.atFine().log("%s %s %s", operation, id, indexEvent);
    IndexingRetry<T> retry = new IndexingRetry<>(id, indexEvent);
    if (inFlightIndexing.put(id, retry) != null) {
      try {
        Context.setForwardedEvent(true);
        return CompletableFuture.completedFuture(createResult(indexOnce(id), id));
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        Context.unsetForwardedEvent();
      }
    }
    try {
      Context.setForwardedEvent(true);
      switch (operation) {
        case INDEX:
          return doIndex(id);
        case DELETE:
          return doDelete(id, indexEvent);
        default:
          log.atSevere().log("unexpected operation: %s", operation);
          return CompletableFuture.completedFuture(createResult(false, id));
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  protected IndexingRetryResult createResult(boolean isSuccessful, T id) {
    return new IndexingRetryResult(isSuccessful, inFlightIndexing.get(id));
  }
}
