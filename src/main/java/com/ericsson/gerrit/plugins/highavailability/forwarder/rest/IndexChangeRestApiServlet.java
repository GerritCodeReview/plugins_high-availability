// Copyright (C) 2015 Ericsson
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
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
class IndexChangeRestApiServlet extends AbstractIndexRestApiServlet<Change.Id> {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(IndexChangeRestApiServlet.class);

  private final ChangeIndexer indexer;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  IndexChangeRestApiServlet(ChangeIndexer indexer, SchemaFactory<ReviewDb> schemaFactory) {
    this.indexer = indexer;
    this.schemaFactory = schemaFactory;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    process(req, rsp, "index");
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    process(req, rsp, "delete");
  }

  private void process(HttpServletRequest req, HttpServletResponse rsp, String operation) {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding(UTF_8.name());
    String path = req.getPathInfo();
    String changeId = path.substring(path.lastIndexOf('/') + 1);
    Change.Id id = Change.Id.parse(changeId);
    try {
      Context.setForwardedEvent(true);
      index(id, operation);
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      logger.error("Unable to update change index", e);
    } catch (OrmException e) {
      String msg = "Error trying to find a change \n";
      sendError(rsp, SC_NOT_FOUND, msg);
      logger.debug(msg, e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private void index(Change.Id id, String operation) throws IOException, OrmException {
    AtomicInteger changeIdLock = getAndIncrementIdLock(id);
    synchronized (changeIdLock) {
      if ("index".equals(operation)) {
        try (ReviewDb db = schemaFactory.open()) {
          Change change = db.changes().get(id);
          if (change == null) {
            indexer.delete(id);
            return;
          }
          indexer.index(db, change);
        }
        logger.debug("Change {} successfully indexed", id);
      }
      if ("delete".equals(operation)) {
        indexer.delete(id);
        logger.debug("Change {} successfully deleted from index", id);
      }
    }
    if (changeIdLock.decrementAndGet() == 0) {
      removeIdLock(id);
    }
  }
}
