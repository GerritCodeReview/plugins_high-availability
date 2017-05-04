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
package com.ericsson.gerrit.plugins.highavailability.peers;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.base.Optional;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Provider which uses JGroups to find the peer gerrit instances. On startup every gerrit instance
 * joins a jgroups channel. Whenever the set of channel members changes each gerrit server publishes
 * his url to all channel members.
 *
 * This provider maintains a list of all members who joined the jgroups channel. This may be more
 * than two. But will always pick the first node who sent it's url as the peer to be returned by
 * {@link #get()}. It will stick to returning that node until that node leaves the jgroups channel.
 */
@Singleton
public class JGroupsPeerInfoProvider extends ReceiverAdapter
    implements Provider<Optional<PeerInfo>>, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(JGroupsPeerInfoProvider.class);
  private String myUrl;
  private JChannel channel;
  private HashMap<Address, String> urlsByAddress = new HashMap<Address, String>();
  private Optional<PeerInfo> peerInfo;
  private Address peerAddress = null;
  private String channelName;
  private boolean preferIPv4;

  @Inject
  JGroupsPeerInfoProvider(@GerritServerConfig Config srvConfig, Configuration pluginConfiguration)
      throws UnknownHostException, URISyntaxException {
    String hostName = InetAddress.getLocalHost().getHostName();
    URIish u = new URIish(srvConfig.getString("httpd", null, "listenUrl"));
    this.myUrl = u.setHost(hostName).toString();
    channelName = pluginConfiguration.getJGroupsChannelName();
    preferIPv4 = pluginConfiguration.getPreferIPv4();
  }

  @Override
  public void receive(Message msg) {
    String url = (String) msg.getObject();
    synchronized (urlsByAddress) {
      urlsByAddress.put(msg.getSrc(), url);
      log.info("receive(): received '{}' from {}", url, msg.getSrc());
      if (peerAddress == null) {
        peerInfo = Optional.of(new PeerInfo(url));
        peerAddress = msg.getSrc();
        log.info("receive(): Set new peerInfo: {}", url);
      }
    }
  }

  @Override
  public void viewAccepted(View view) {
    log.info("viewAccepted(view: {}) called", view);
    synchronized (urlsByAddress) {
      urlsByAddress.keySet().retainAll(view.getMembers());
      if (peerAddress != null && !urlsByAddress.containsKey(peerAddress)) {
        log.info("viewAccepted(): removed peerInfo");
        peerAddress = null;
        peerInfo = Optional.absent();
      }
    }
    if (view.size() > 1) {
      try {
        channel.send(new Message(null, myUrl));
      } catch (Exception e) {
        // channel communication caused an error. Can't do much about
        // it.
        log.error("Sending a message over jgroups channel caused an error", e);
      }
    }
  }

  @Override
  public void start() {
    try {
      if (preferIPv4) {
        System.setProperty("java.net.preferIPv4Stack", "true");
      }
      channel = new JChannel();
      channel.setReceiver(this);
      channel.setDiscardOwnMessages(true);
      channel.connect(channelName);
      log.info("Succesfully joined jgroups channel {}", channel);
    } catch (Exception e) {
      log.error("received and exception while trying to join jgroups channel", e);
    }
  }

  @Override
  public void stop() {
    log.info("left jgroups channel");
    channel.close();
    synchronized (urlsByAddress) {
      peerInfo = Optional.absent();
      peerAddress = null;
      urlsByAddress.clear();
    }
  }

  @Override
  public Optional<PeerInfo> get() {
    return peerInfo;
  }
}
