// Copyright (C) 2023 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.jgroups.blocks.MessageDispatcher;

@Singleton
public class OnStartStop implements LifecycleListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final MessageDispatcher dispatcher;

  @Inject
  public OnStartStop(MessageDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    log.atInfo().log("Closing JChannel");
    dispatcher.getChannel().close();
    try {
      dispatcher.close();
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Could not close the MessageDispatcher");
    }
  }
}
