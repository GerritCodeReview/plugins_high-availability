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

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Index a group using {@link GroupIndexer}. This class is meant to be used on the receiving side of
 * the {@link Forwarder} since it will prevent indexed group to be forwarded again causing an
 * infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same group uuid
 */
@Singleton
public class ForwardedIndexGroupHandler extends ForwardedIndexingHandler<AccountGroup.UUID> {
  private final GroupIndexer indexer;

  @Inject
  ForwardedIndexGroupHandler(GroupIndexer indexer) {
    this.indexer = indexer;
  }

  @Override
  protected CompletableFuture<Boolean> doIndex(
      AccountGroup.UUID uuid, Optional<IndexEvent> indexEvent) {
    indexer.index(uuid);
    log.atFine().log("Group %s successfully indexed", uuid);
    return CompletableFuture.completedFuture(true);
  }

  @Override
  protected CompletableFuture<Boolean> doDelete(
      AccountGroup.UUID uuid, Optional<IndexEvent> indexEvent) {
    throw new UnsupportedOperationException("Delete from group index not supported");
  }
}
