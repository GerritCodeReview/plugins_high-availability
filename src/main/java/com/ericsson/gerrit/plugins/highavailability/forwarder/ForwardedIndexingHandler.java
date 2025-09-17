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
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
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
  private final Set<T> inFlightIndexing = Collections.newSetFromMap(new ConcurrentHashMap<>());

  public enum Operation {
    INDEX,
    DELETE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  protected abstract CompletableFuture<Boolean> doIndex(T id, Optional<IndexEvent> indexEvent)
      throws IOException;

  protected abstract CompletableFuture<Boolean> doDelete(T id, Optional<IndexEvent> indexEvent)
      throws IOException;

  /**
   * Index an item in the local node, indexing will not be forwarded to the other node.
   *
   * @param id The id to index.
   * @param operation The operation to do; index or delete
   * @param indexEvent The index event details.
   * @throws IOException If an error occur while indexing.
   */
  public CompletableFuture<Boolean> index(
      T id, Operation operation, Optional<IndexEvent> indexEvent) throws IOException {
    log.atFine().log("%s %s %s", operation, id, indexEvent);
    if (inFlightIndexing.add(id)) {
      try {
        Context.setForwardedEvent(true);
        switch (operation) {
          case INDEX:
            return doIndex(id, indexEvent)
                .thenApplyAsync(
                    r -> {
                      return r;
                    });
          case DELETE:
            return doDelete(id, indexEvent)
                .thenApplyAsync(
                    r -> {
                      return r;
                    });
          default:
            log.atSevere().log("unexpected operation: %s", operation);
            return CompletableFuture.completedFuture(false);
        }
      } catch (Exception e) {
        throw e;
      } finally {
        Context.unsetForwardedEvent();
        inFlightIndexing.remove(id);
      }
    }
    throw new InFlightIndexedException(
        String.format("Indexing for %s %s %s already in flight", operation, id, indexEvent));
  }
}
