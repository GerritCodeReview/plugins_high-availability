// Copyright (C) 2017 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.util;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IndexAccounts {
  private static final Logger log = LoggerFactory.getLogger(IndexAccounts.class);
  private static final Map<Account.Id, AtomicInteger> accountIdLocks = new HashMap<>();

  private final AccountIndexer indexer;

  @Inject
  public IndexAccounts(AccountIndexer indexer) {
    this.indexer = indexer;
  }

  public void index(Account.Id id) throws IOException {
    AtomicInteger accountIdLock = getAndIncrementAccountIdLock(id);
    synchronized (accountIdLock) {
      indexer.index(id);
      log.debug("Account {} successfully indexed", id);
    }
    if (accountIdLock.decrementAndGet() == 0) {
      removeAccountIdLock(id);
    }
  }

  private AtomicInteger getAndIncrementAccountIdLock(Account.Id id) {
    synchronized (accountIdLocks) {
      AtomicInteger accountIdLock = accountIdLocks.get(id);
      if (accountIdLock == null) {
        accountIdLock = new AtomicInteger(1);
        accountIdLocks.put(id, accountIdLock);
      } else {
        accountIdLock.incrementAndGet();
      }
      return accountIdLock;
    }
  }

  private void removeAccountIdLock(Account.Id id) {
    synchronized (accountIdLocks) {
      if (accountIdLocks.get(id).get() == 0) {
        accountIdLocks.remove(id);
      }
    }
  }
}
