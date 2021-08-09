// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.event;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.inject.Inject;
import java.util.concurrent.Executor;

class EventHandler implements EventListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final Executor executor;
  private final Forwarder forwarder;
  private final String pluginName;
  private final Configuration config;

  @Inject
  EventHandler(
      Forwarder forwarder,
      @EventExecutor Executor executor,
      @PluginName String pluginName,
      Configuration config) {
    this.forwarder = forwarder;
    this.executor = executor;
    this.pluginName = pluginName;
    this.config = config;
  }

  @Override
  public void onEvent(Event event) {
    if (!Context.isForwardedEvent() && event instanceof ProjectEvent) {
      if (config.event().refFilteringEnabled()
          && event instanceof Event
          && config.event().isIgnoredEvent(event.type)) {
        log.atInfo().log("Event filtered %s", event.instanceId);
        return;
      }
      executor.execute(new EventTask(event));
    }
  }

  class EventTask implements Runnable {
    private final Event event;

    EventTask(Event event) {
      this.event = event;
    }

    @Override
    public void run() {
      forwarder.send(event);
    }

    @Override
    public String toString() {
      return String.format("[%s] Send event '%s' to target instance", pluginName, event.type);
    }
  }
}
