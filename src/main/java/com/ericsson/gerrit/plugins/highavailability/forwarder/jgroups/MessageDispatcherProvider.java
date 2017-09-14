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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.JGroups;
import com.ericsson.gerrit.plugins.highavailability.peers.jgroups.InetAddressFinder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.net.InetAddress;
import java.util.Optional;
import org.jgroups.JChannel;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class MessageDispatcherProvider implements Provider<MessageDispatcher> {
  private static final Logger log = LoggerFactory.getLogger(MessageDispatcherProvider.class);

  private final InetAddressFinder finder;
  private final JGroups jgroupsConfig;
  private final Provider<JChannel> channelProvider;
  private final RequestHandler requestHandler;

  @Inject
  MessageDispatcherProvider(
      InetAddressFinder finder,
      Configuration pluginConfiguration,
      Provider<JChannel> channelProvider,
      RequestHandler requestHandler) {
    this.finder = finder;
    this.jgroupsConfig = pluginConfiguration.jgroups();
    this.channelProvider = channelProvider;
    this.requestHandler = requestHandler;
  }

  // The channel is only closed when the plugin is unloaded
  @SuppressWarnings("resource")
  @Override
  public MessageDispatcher get() {
    try {
      JChannel channel = channelProvider.get();
      Optional<InetAddress> address = finder.findAddress();
      if (address.isPresent()) {
        log.debug(
            "Channel bound to {} using protocol stack:\n {}",
            address.get(),
            channel.getProtocolStack().printProtocolSpecAsXML());
        channel.getProtocolStack().getTransport().setBindAddress(address.get());
      }
      channel.setDiscardOwnMessages(true);
      channel.connect(jgroupsConfig.clusterName());
      log.info(
          "Channel {} successfully joined jgroups cluster {}",
          channel.getName(),
          jgroupsConfig.clusterName());
      return new MessageDispatcher(channel, requestHandler);
    } catch (Exception e) {
      throw new ProvisionException("Unable to connect to cluster", e);
    }
  }
}
