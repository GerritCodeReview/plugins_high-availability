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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexAccountHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;

public class AccountReindexRunnable extends ReindexRunnable<AccountState> {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final ForwardedIndexAccountHandler accountIdx;

  private final Accounts accounts;

  @Inject
  public AccountReindexRunnable(
      ForwardedIndexAccountHandler accountIdx,
      IndexTs indexTs,
      OneOffRequestContext ctx,
      Accounts accounts) {
    super(AbstractIndexRestApiServlet.IndexName.ACCOUNT, indexTs, ctx);
    this.accountIdx = accountIdx;
    this.accounts = accounts;
  }

  @Override
  protected Iterable<AccountState> fetchItems() throws Exception {
    return accounts.all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(AccountState as, Timestamp sinceTs) {
    try {
      Account a = as.account();
      Timestamp accountTs = Timestamp.from(a.registeredOn());
      if (accountTs.after(sinceTs)) {
        log.atInfo().log("Index %s/%s/%s/%s", a.id(), a.fullName(), a.preferredEmail(), accountTs);
        accountIdx.index(a.id(), Operation.INDEX, Optional.empty());
        return Optional.of(accountTs);
      }
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Reindex failed");
    }
    return Optional.empty();
  }
}
