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
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
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
 * more than two. But will always pick the first node which sent its url as the peer to be returned
 * by {@link #get()}. It will continue to return that node until that node leaves the jgroups
 * cluster.
 */
@Singleton
public class JGroupsPeerInfoProvider
    implements Receiver, Provider<Set<PeerInfo>>, LifecycleListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private static final String JGROUPS_LOG_FACTORY_PROPERTY = "jgroups.logging.log_factory_class";

  static {
    if (System.getProperty(JGROUPS_LOG_FACTORY_PROPERTY) == null) {
      System.setProperty(JGROUPS_LOG_FACTORY_PROPERTY, SLF4JLogFactory.class.getName());
    }
  }

  private final Configuration.JGroups jgroupsConfig;
  private final Configuration.JGroupsKubernetes jgroupsKubernetesConfig;
  private final InetAddressFinder finder;
  private final String myUrl;

  private JChannel channel;
  private Optional<PeerInfo> peerInfo = Optional.empty();
  private Address peerAddress;

  @Inject
  JGroupsPeerInfoProvider(
      Configuration pluginConfiguration, InetAddressFinder finder, MyUrlProvider myUrlProvider) {
    this.jgroupsConfig = pluginConfiguration.jgroups();
    this.jgroupsKubernetesConfig = pluginConfiguration.jgroupsKubernetes();
    this.finder = finder;
    this.myUrl = myUrlProvider.get();
  }

  @Override
  public void receive(Message msg) {
    synchronized (this) {
      if (peerAddress != null) {
        return;
      }
      peerAddress = msg.getSrc();
      String url = (String) msg.getObject();
      peerInfo = Optional.of(new PeerInfo(url));
      log.atInfo().log("receive(): Set new peerInfo: %s", url);
    }
  }

  @Override
  public void viewAccepted(View view) {
    log.atInfo().log("viewAccepted(view: %s) called", view);
    synchronized (this) {
      if (view.getMembers().size() > 2) {
        log.atWarning().log(
            "%d members joined the jgroups cluster %s (%s). "
                + " Only two members are supported. Members: %s",
            view.getMembers().size(),
            jgroupsConfig.clusterName(),
            channel.getName(),
            view.getMembers());
      }
      if (peerAddress != null && !view.getMembers().contains(peerAddress)) {
        log.atInfo().log("viewAccepted(): removed peerInfo");
        peerAddress = null;
        peerInfo = Optional.empty();
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
      channel = getChannel();
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

  private JChannel getChannel() throws Exception {
    Optional<Path> protocolStack = jgroupsConfig.protocolStack();
    try {
      if (protocolStack.isPresent()) {
        return new JChannel(protocolStack.get().toString());
      }
      if (jgroupsConfig.useKubernetes()) {
        if (jgroupsKubernetesConfig.namespace() != null) {
          System.setProperty("KUBERNETES_NAMESPACE", jgroupsKubernetesConfig.namespace());
        }
        if (!jgroupsKubernetesConfig.labels().isEmpty()) {
          System.setProperty(
              "KUBERNETES_LABELS", String.join(",", jgroupsKubernetesConfig.labels()));
        }
        return new JChannel(getClass().getResource("kubernetes.xml").toString());
      }
      return new JChannel();
    } catch (Exception e) {
      log.atSevere().withCause(e).log(
          "Unable to create a channel with protocol stack: %s",
          protocolStack.isPresent() ? protocolStack : "default");
      throw e;
    }
  }

  @VisibleForTesting
  void setChannel(JChannel channel) {
    this.channel = channel;
  }

  @VisibleForTesting
  void setPeerInfo(Optional<PeerInfo> peerInfo) {
    this.peerInfo = peerInfo;
  }

  @Override
  public Set<PeerInfo> get() {
    return peerInfo.isPresent() ? ImmutableSet.of(peerInfo.get()) : ImmutableSet.of();
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
    peerInfo = Optional.empty();
    peerAddress = null;
  }

  @VisibleForTesting
  Address getPeerAddress() {
    return peerAddress;
  }

  @VisibleForTesting
  void setPeerAddress(Address peerAddress) {
    this.peerAddress = peerAddress;
  }
}
