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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractIndexRestApiServlet<T> extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(AbstractIndexRestApiServlet.class);
  private final Map<T, AtomicInteger> idLocks = new HashMap<>();

  protected AtomicInteger getAndIncrementIdLock(T id) {
    synchronized (idLocks) {
      AtomicInteger lock = idLocks.get(id);
      if (lock == null) {
        lock = new AtomicInteger(1);
        idLocks.put(id, lock);
      } else {
        lock.incrementAndGet();
      }
      return lock;
    }
  }

  protected void removeIdLock(T id) {
    synchronized (idLocks) {
      idLocks.remove(id);
    }
  }

  protected static void sendError(HttpServletResponse rsp, int statusCode, String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      logger.error("Failed to send error messsage: " + e.getMessage(), e);
    }
  }
}
