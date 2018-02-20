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

package com.ericsson.gerrit.plugins.highavailability;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.IndexTs;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountReindexRunnable extends ReindexRunnable<Account> {
  private static final Logger log = LoggerFactory.getLogger(AccountReindexRunnable.class);

  private final AccountIndexer accountIdx;

  @Inject
  public AccountReindexRunnable(
      AccountIndexer accountIdx, IndexTs indexTs, OneOffRequestContext ctx) {
    super(AbstractIndexRestApiServlet.IndexName.ACCOUNT, indexTs, ctx);
    this.accountIdx = accountIdx;
  }

  @Override
  protected ResultSet<Account> fetchItems(ReviewDb db) throws OrmException {
    return db.accounts().all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(ReviewDb db, Account a, Timestamp sinceTs) {
    try {
      Timestamp accountTs = a.getRegisteredOn();
      if (accountTs.after(sinceTs)) {
        log.info("Index {}/{}/{}/{}", a.getId(), a.getFullName(), a.getPreferredEmail(), accountTs);
        accountIdx.index(a.getId());
        return Optional.of(accountTs);
      }
    } catch (IOException e) {
      log.error("Reindex failed", e);
    }
    return Optional.empty();
  }
}
