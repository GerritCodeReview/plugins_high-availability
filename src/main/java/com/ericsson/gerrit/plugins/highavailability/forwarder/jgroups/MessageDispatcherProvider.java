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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.JGroups;
import com.ericsson.gerrit.plugins.highavailability.peers.jgroups.InetAddressFinder;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.net.InetAddress;
import java.util.Optional;
import org.jgroups.JChannel;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;

@Singleton
public class MessageDispatcherProvider implements Provider<MessageDispatcher> {
  private static FluentLogger log = FluentLogger.forEnclosingClass();

  private final InetAddressFinder finder;
  private final JGroups jgroupsConfig;
  private final RequestHandler requestHandler;
  private final JChannel channel;

  @Inject
  MessageDispatcherProvider(
      InetAddressFinder finder,
      Configuration pluginConfiguration,
      RequestHandler requestHandler,
      JChannel channel) {
    this.finder = finder;
    this.jgroupsConfig = pluginConfiguration.jgroups();
    this.requestHandler = requestHandler;
    this.channel = channel;
  }

  @Override
  public MessageDispatcher get() {
    try {
      Optional<InetAddress> address = finder.findAddress();
      if (address.isPresent()) {
        channel.getProtocolStack().getTransport().setBindAddress(address.get());
      }
      channel.setDiscardOwnMessages(true);
      channel.connect(jgroupsConfig.clusterName());
      log.atInfo().log("Succesfully joined jgroups cluster %s", channel.getClusterName());
      MessageDispatcher dispatcher = new MessageDispatcher(channel, requestHandler);
      return dispatcher;
    } catch (Exception e) {
      throw new ProvisionException("Could not create a JChannel", e);
    }
  }
}
