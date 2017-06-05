// Copyright (C) 2017 Ericsson
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

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class IndexAccountRestApiServlet extends AbstractIndexRestApiServlet<Account.Id> {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(IndexAccountRestApiServlet.class);

  private final AccountIndexer indexer;

  @Inject
  IndexAccountRestApiServlet(AccountIndexer indexer) {
    this.indexer = indexer;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding("UTF-8");
    String path = req.getPathInfo();
    String accountId = path.substring(path.lastIndexOf('/') + 1);
    Account.Id id = Account.Id.parse(accountId);
    try {
      Context.setForwardedEvent(true);
      index(id);
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      logger.error("Unable to update account index", e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private void index(Account.Id id) throws IOException {
    AtomicInteger accountIdLock = getAndIncrementIdLock(id);
    synchronized (accountIdLock) {
      indexer.index(id);
      logger.debug("Account {} successfully indexed", id);
    }
    if (accountIdLock.decrementAndGet() == 0) {
      removeIdLock(id);
    }
  }
}
