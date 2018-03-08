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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.common.util.concurrent.Striped;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractIndexRestApiServlet<T> extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(AbstractIndexRestApiServlet.class);
  private static final Optional<LocalDateTime> NO_TS = Optional.empty();

  private final IndexName indexName;
  private final boolean allowDelete;
  private final IndexTs indexTs;
  private final Striped<Lock> idLocks;

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

  AbstractIndexRestApiServlet(IndexName indexName, boolean allowDelete, IndexTs indexTs) {
    this.indexName = indexName;
    this.allowDelete = allowDelete;
    this.idLocks = Striped.lock(10);
    this.indexTs = indexTs;
  }

  AbstractIndexRestApiServlet(IndexName indexName, IndexTs indexTs) {
    this(indexName, false, indexTs);
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
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding(UTF_8.name());
    String path = req.getPathInfo();
    T id = parse(path.substring(path.lastIndexOf('/') + 1));
    logger.debug("{} {} {}", operation, indexName, id);
    try {
      Context.setForwardedEvent(true);
      Lock idLock = idLocks.get(id);
      idLock.lock();
      try {
        index(id, operation);
      } finally {
        idLock.unlock();
      }
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      logger.error("Unable to update {} index", indexName, e);
    } catch (OrmException e) {
      String msg = String.format("Error trying to find %s \n", indexName);
      sendError(rsp, SC_NOT_FOUND, msg);
      logger.debug(msg, e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private void sendError(HttpServletResponse rsp, int statusCode, String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      logger.error("Failed to send error messsage: {}", e.getMessage(), e);
    }
  }

  protected void updateIndexTs(Change.Id id, LocalDateTime ts) {
    indexTs.update(indexName, Operation.INDEX, "" + id.id, Optional.of(ts));
  }

  protected void deleteIndexTs(Change.Id id) {
    indexTs.update(indexName, Operation.DELETE, "" + id.id, NO_TS);
  }
}
