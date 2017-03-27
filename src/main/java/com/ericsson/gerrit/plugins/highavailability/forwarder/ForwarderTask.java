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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.ericsson.gerrit.plugins.highavailability.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ForwarderTask implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ForwarderTask.class);

  private final Configuration cfg;

  protected ForwarderTask(Configuration cfg) {
    this.cfg = cfg;
  }

  @Override
  public void run() {
    beforeRun();
    int executionCount = 0;
    for (;;) {
      executionCount++;
      try {
        forward();
        break;
      } catch (ForwardingException e) {
        if (!e.isRecoverable()) {
          log.error("Task '" + toString() + "' failed", e);
          break;
        }
        if (executionCount >= cfg.getMaxTries()) {
          log.error("Task '" + toString() + "' giving up after "
              + cfg.getMaxTries() + " tries", e);
          break;
        }
        try {
          logRetry(e);
          Thread.sleep(cfg.getRetryInterval());
        } catch (InterruptedException ie) {
          log.error(toString() + " task was interrupted, giving up", ie);
          break;
        }
      }
    }
  }

  protected void beforeRun() {
  }

  protected abstract void forward() throws ForwardingException;

  private void logRetry(Throwable cause) {
    if(log.isDebugEnabled()){
      log.debug("Retrying task: " + toString() + " caused by '" + cause + "'");
    }
  }
}
