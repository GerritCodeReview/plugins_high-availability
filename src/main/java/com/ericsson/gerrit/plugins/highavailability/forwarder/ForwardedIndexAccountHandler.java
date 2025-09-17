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

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Index an account using {@link AccountIndexer}. This class is meant to be used on the receiving
 * side of the {@link Forwarder} since it will prevent indexed account to be forwarded again causing
 * an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same account id
 */
@Singleton
public class ForwardedIndexAccountHandler extends ForwardedIndexingHandler<Account.Id> {
  private final AccountIndexer indexer;

  @Inject
  ForwardedIndexAccountHandler(AccountIndexer indexer) {
    this.indexer = indexer;
  }

  @Override
  protected CompletableFuture<Boolean> doIndex(Account.Id id, Optional<IndexEvent> indexEvent) {
    try {
      indexer.index(id);
      log.atFine().log("Account %s successfully indexed", id);
    } catch (RuntimeException e) {
      log.atFine().log("Account %s failed to be indexed", id);
      throw e;
    }
    return CompletableFuture.completedFuture(true);
  }

  @Override
  protected CompletableFuture<Boolean> doDelete(Account.Id id, Optional<IndexEvent> indexEvent) {
    throw new UnsupportedOperationException("Delete from account index not supported");
  }
}
