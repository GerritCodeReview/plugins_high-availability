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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import com.google.common.collect.ImmutableMap;
import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;

/**
 * Provider which uses JGroups to find the peer gerrit instances. On startup every gerrit instance
 * creates its own channel and joins jgroup cluster. Whenever the set of cluster members changes
 * each gerrit server publishes its url to all cluster members (publishes it to all channels).
 *
 * <p>This provider maintains a list of all members which joined the jgroups cluster. This may be
 * more than two. The set of urls of all peers is returned by {@link #get()}. If a node leaves the
 * jgroups cluster it's removed from this set.
 */
@Singleton
public class JGroupsPeerInfoProvider
    implements Receiver, Provider<Set<PeerInfo>>, LifecycleListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final Configuration.JGroups jgroupsConfig;
  private final InetAddressFinder finder;
  private final String myUrl;

  private JChannel channel;
  private Map<Address, PeerInfo> peers = new ConcurrentHashMap<>();

  @Inject
  JGroupsPeerInfoProvider(
      Configuration pluginConfiguration,
      InetAddressFinder finder,
      MyUrlProvider myUrlProvider,
      JChannel channel) {
    this.jgroupsConfig = pluginConfiguration.jgroups();
    this.finder = finder;
    this.myUrl = myUrlProvider.get();
    this.channel = channel;
  }

  @Override
  public void receive(Message msg) {
    String url = (String) msg.getObject();
    if (url == null) {
      return;
    }
    Address addr = msg.getSrc();
    PeerInfo old = peers.put(addr, new PeerInfo(url));
    if (old == null) {
      log.atInfo().log("receive(): Add new peerInfo: %s", url);
    } else {
      log.atInfo().log("receive(): Update peerInfo: from %s to %s", old.getDirectUrl(), url);
    }
  }

  @Override
  public void viewAccepted(View view) {
    log.atInfo().log("viewAccepted(view: %s) called", view);
    Iterator<Map.Entry<Address, PeerInfo>> it = peers.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Address, PeerInfo> e = it.next();
      if (!view.getMembers().contains(e.getKey())) {
        log.atInfo().log("viewAccepted(): removed peerInfo %s", e.getValue().getDirectUrl());
        it.remove();
      }
    }
    if (view.size() > 1) {
      try {
        channel.send(new ObjectMessage(null, myUrl));
      } catch (Exception e) {
        // channel communication caused an error. Can't do much about it.
        log.atSevere().withCause(e).log(
            "Sending a message over channel %s to cluster %s failed",
            channel.getName(), jgroupsConfig.clusterName());
      }
    }
  }

  public void connect() {
    try {
      Optional<InetAddress> address = finder.findAddress();
      if (address.isPresent()) {
        log.atFine().log("Protocol stack: %s", channel.getProtocolStack());
        channel.getProtocolStack().getTransport().setBindAddress(address.get());
        log.atFine().log("Channel bound to %s", address.get());
      } else {
        log.atWarning().log("Channel not bound: address not present");
      }
      channel.setReceiver(this);
      channel.setDiscardOwnMessages(true);
      channel.connect(jgroupsConfig.clusterName());
      log.atInfo().log(
          "Channel %s successfully joined jgroups cluster %s",
          channel.getName(), jgroupsConfig.clusterName());
    } catch (Exception e) {
      if (channel != null) {
        log.atSevere().withCause(e).log(
            "joining cluster %s (channel %s) failed",
            jgroupsConfig.clusterName(), channel.getName());
      } else {
        log.atSevere().withCause(e).log("joining cluster %s failed", jgroupsConfig.clusterName());
      }
    }
  }

  @VisibleForTesting
  void setChannel(JChannel channel) {
    this.channel = channel;
  }

  @Override
  public Set<PeerInfo> get() {
    return ImmutableSet.copyOf(peers.values());
  }

  @Override
  public void start() {
    connect();
  }

  @Override
  public void stop() {
    if (channel != null) {
      log.atInfo().log(
          "closing jgroups channel %s (cluster %s)",
          channel.getName(), jgroupsConfig.clusterName());
      channel.close();
    }
    peers.clear();
  }

  @VisibleForTesting
  Map<Address, PeerInfo> getPeers() {
    return ImmutableMap.copyOf(peers);
  }

  @VisibleForTesting
  void addPeer(Address address, PeerInfo info) {
    if (address == null) {
      return;
    }
    this.peers.put(address, info);
  }
}
