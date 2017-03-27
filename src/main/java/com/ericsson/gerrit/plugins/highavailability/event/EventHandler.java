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

package com.ericsson.gerrit.plugins.highavailability.event;

import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.inject.Inject;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwarderTask;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardingException;

import java.util.concurrent.Executor;

class EventHandler implements EventListener {
  private final Executor executor;
  private final Forwarder forwarder;
  private final String pluginName;
  private final Configuration cfg;

  @Inject
  EventHandler(Forwarder forwarder,
      @EventExecutor Executor executor,
      @PluginName String pluginName,
      Configuration cfg) {
    this.forwarder = forwarder;
    this.executor = executor;
    this.pluginName = pluginName;
    this.cfg = cfg;
  }

  @Override
  public void onEvent(Event event) {
    if (!Context.isForwardedEvent() && event instanceof ProjectEvent) {
      executor.execute(new EventTask(event));
    }
  }

  class EventTask extends ForwarderTask {
    private Event event;

    EventTask(Event event) {
      super(cfg);
      this.event = event;
    }

    @Override
    public void forward() throws ForwardingException {
      forwarder.send(event);
    }

    @Override
    public String toString() {
      return String.format("[%s] Send event '%s' to target instance",
          pluginName, event.type);
    }
  }
}
