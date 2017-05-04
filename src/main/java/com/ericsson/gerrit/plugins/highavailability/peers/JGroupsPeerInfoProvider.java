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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.regex.Pattern;

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
 * its url to all channel members.
 *
 * This provider maintains a list of all members which joined the jgroups channel. This may be more
 * than two. But will always pick the first node which sent it's url as the peer to be returned by
 * {@link #get()}. It will continue to return that node until that node leaves the jgroups channel.
 */
@Singleton
public class JGroupsPeerInfoProvider extends ReceiverAdapter
    implements Provider<Optional<PeerInfo>>, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(JGroupsPeerInfoProvider.class);
  private String myUrl;
  private JChannel channel;
  private Optional<PeerInfo> peerInfo = Optional.absent();
  private Address peerAddress;
  private String channelName;
  private boolean preferIPv4;
  private Pattern skipInterfacePattern;

  @Inject
  JGroupsPeerInfoProvider(@GerritServerConfig Config srvConfig, Configuration pluginConfiguration)
      throws UnknownHostException, URISyntaxException {
    String hostName = InetAddress.getLocalHost().getHostName();
    URIish u = new URIish(srvConfig.getString("httpd", null, "listenUrl"));
    this.myUrl = u.setHost(hostName).toString();
    channelName = pluginConfiguration.jgroups().clusterName();
    preferIPv4 = pluginConfiguration.jgroups().preferIPv4();
    skipInterfacePattern = Pattern.compile(pluginConfiguration.jgroups().skipInterfacePattern());
    connect();
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
      log.info("receive(): Set new peerInfo: {}", url);
    }
  }

  @Override
  public void viewAccepted(View view) {
    log.info("viewAccepted(view: {}) called", view);

    synchronized (this) {
      if (view.getMembers().size() > 2) {
        log.warn(
            "{} members try to connect to the high-availability cluster. Only two members are supported. members: {}",
            view.getMembers().size(), view.getMembers());
      }
      if (peerAddress != null && !view.getMembers().contains(peerAddress)) {
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
        log.error("Sending a message over jgroups channel {} caused an error", channelName, e);
      }
    }
  }

  public void connect() {
    try {
      if (preferIPv4) {
        System.setProperty("java.net.preferIPv4Stack", "true");
      }
      channel = new JChannel();
      InetAddress address = findDefaultAddress();
      if (address != null) {
        channel.getProtocolStack().getTransport().setBindAddress(address);
      }
      channel.setReceiver(this);
      channel.setDiscardOwnMessages(true);
      channel.connect(channelName);
      log.info("Succesfully joined jgroups channel {}", channel);
    } catch (Exception e) {
      log.error("received an exception while trying to join jgroups channel", e);
    }
  }

  /**
   * Iterate over all network interfaces and return the first appropriate address. Interfaces which
   * are loopback interfaces, or down or which don't support multicast are not inspected. Interfaces
   * which name match {@link #skipInterfacePattern} are also ignored. By that it is possible to skip
   * interfaces which should not be used by jgroups (e.g. 'lo0', 'utun0' on MacOS).
   *
   * This method is an alternative to {@link Inet4Address#getLocalHost()} which caused problems on
   * hosts with multiple network interfaces. It was not possible to control which of the network
   * interfaces should be chosen. Trying to transport messages over the wrong network interface
   * caused JGroups initialization to fail.
   *
   * @return an InetAddress or <code>null</code> if no appropriate address could be found
   */
  private InetAddress findDefaultAddress() throws SocketException {
    InetAddress ret = null;
    Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
    while (networkInterfaces.hasMoreElements()) {
      NetworkInterface ni = networkInterfaces.nextElement();
      if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast()) {
        continue;
      }
      if (skipInterfacePattern.matcher(ni.getName()).matches()) {
        continue;
      }
      Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
      while (inetAddresses.hasMoreElements()) {
        ret = inetAddresses.nextElement();
        if (preferIPv4 && (ret instanceof Inet4Address)) {
          return ret;
        }
      }
    }
    return ret;
  }

  @Override
  public void stop() {
    log.info("closing jgroups channel {}", channelName);
    channel.close();
    peerInfo = Optional.absent();
    peerAddress = null;
  }

  @Override
  public Optional<PeerInfo> get() {
    return peerInfo;
  }

  @Override
  public void start() {}
}
