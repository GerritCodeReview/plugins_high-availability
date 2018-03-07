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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractIndexRestApiServlet<T> extends AbstractRestApiServlet {
  private static final long serialVersionUID = -1L;
  private static final Optional<LocalDateTime> NO_TS = Optional.empty();

  private final ForwardedIndexingHandler<T> forwardedIndexingHandler;
  private final IndexName indexName;
  private final boolean allowDelete;
  private final IndexTs indexTs;

  enum Operation {
    INDEX,
    DELETE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  public enum IndexName {
    CHANGE,
    ACCOUNT,
    GROUP;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  abstract T parse(String id);

  abstract void index(T id, Operation operation) throws IOException, OrmException;

  AbstractIndexRestApiServlet(
      ForwardedIndexingHandler<T> forwardedIndexingHandler,
      IndexName indexName,
      boolean allowDelete,
      IndexTs indexTs) {
    this.forwardedIndexingHandler = forwardedIndexingHandler;
    this.indexName = indexName;
    this.allowDelete = allowDelete;
    this.indexTs = indexTs;
  }

  AbstractIndexRestApiServlet(
      ForwardedIndexingHandler<T> forwardedIndexingHandler, IndexName indexName, IndexTs indexTs) {
    this(forwardedIndexingHandler, indexName, false, indexTs);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    process(req, rsp, Operation.INDEX);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp) {
    if (!allowDelete) {
      sendError(
          rsp, SC_METHOD_NOT_ALLOWED, String.format("cannot delete %s from index", indexName));
    } else {
      process(req, rsp, Operation.DELETE);
    }
  }

  private void process(HttpServletRequest req, HttpServletResponse rsp, Operation operation) {
    setHeaders(rsp);
    String path = req.getPathInfo();
    T id = parse(path.substring(path.lastIndexOf('/') + 1));
    try {
      forwardedIndexingHandler.index(id, operation);
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      logger.error("Unable to update {} index", indexName, e);
    } catch (OrmException e) {
      String msg = String.format("Error trying to find %s", indexName);
      sendError(rsp, SC_NOT_FOUND, msg);
      logger.debug(msg, e);
    }
  }

  protected void updateIndexTs(Change.Id id, LocalDateTime ts) {
    indexTs.update(indexName, Operation.INDEX, "" + id.id, Optional.of(ts));
  }

  protected void deleteIndexTs(Change.Id id) {
    indexTs.update(indexName, Operation.DELETE, "" + id.id, NO_TS);
  }
}
