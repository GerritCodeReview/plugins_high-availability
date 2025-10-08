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

import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.Command;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandProcessor;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandsGson;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jgroups.Message;
import org.jgroups.blocks.RequestHandler;

@Singleton
public class JGroupsMessageProcessor implements RequestHandler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final Gson gson;
  private final CommandProcessor processor;

  @Inject
  JGroupsMessageProcessor(@CommandsGson Gson gson, CommandProcessor processor) {
    this.gson = gson;
    this.processor = processor;
  }

  @Override
  public Object handle(Message msg) {
    return processor.handle(getCommand(msg));
  }

  private Command getCommand(Message msg) {
    try {
      String s = (String) msg.getObject();
      log.atFine().log("Received message: %s", s);
      return gson.fromJson(s, Command.class);
    } catch (RuntimeException e) {
      log.atSevere().withCause(e).log("Error parsing message %s", msg.getObject());
      throw e;
    }
  }
}
