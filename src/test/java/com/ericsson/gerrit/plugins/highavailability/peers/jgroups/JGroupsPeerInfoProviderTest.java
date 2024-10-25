// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.stack.IpAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.Test.None;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JGroupsPeerInfoProviderTest {

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Configuration pluginConfigurationMock;

  private InetAddressFinder finder;
  private JGroupsPeerInfoProvider jGroupsPeerInfoProvider;
  private PeerInfo peerInfo;
  @Mock private JChannel channel;
  @Mock private MyUrlProvider myUrlProviderTest;
  @Mock private Message message;
  @Mock private Address peerAddress;
  @Mock private View view;
  @Mock private List<Address> members;

  @Before
  public void setUp() throws Exception {
    finder = new InetAddressFinder(pluginConfigurationMock);
    JChannel channel = new JChannelProvider(pluginConfigurationMock).get();
    jGroupsPeerInfoProvider =
        new JGroupsPeerInfoProvider(pluginConfigurationMock, finder, myUrlProviderTest, channel);
    peerInfo = new PeerInfo("test message");
    channel.setName("testChannel");
  }

  @Test
  public void testRecieveWhenPeerAddressIsNull() {
    when(message.getSrc()).thenReturn(peerAddress);
    when(message.getObject()).thenReturn("test message");

    jGroupsPeerInfoProvider.receive(message);

    assertThat(jGroupsPeerInfoProvider.getPeers()).containsKey(peerAddress);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    for (PeerInfo testPeerInfo : testPeerInfoSet) {
      assertThat(testPeerInfo.getDirectUrl()).contains("test message");
    }
    assertThat(testPeerInfoSet.size()).isEqualTo(1);
  }

  @Test
  public void testReceiveWhenPeerAddressIsNotNull() throws Exception {
    lenient().when(message.getSrc()).thenReturn(peerAddress);
    when(message.getObject()).thenReturn(null);

    jGroupsPeerInfoProvider.receive(message);

    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet).isEmpty();
  }

  @Test
  public void testReceiveMultiplePeers() throws Exception {
    final IpAddress ADDR1 = new IpAddress("192.168.1.5:7800");
    final IpAddress ADDR2 = new IpAddress("192.168.1.6:7800");
    final IpAddress ADDR3 = new IpAddress("192.168.1.7:7800");
    final String URL1 = "URL1";
    final String URL2 = "URL2";
    final String URL3 = "URL3";

    when(message.getSrc()).thenReturn(ADDR1);
    when(message.getObject()).thenReturn(URL1);
    jGroupsPeerInfoProvider.receive(message);

    when(message.getSrc()).thenReturn(ADDR2);
    when(message.getObject()).thenReturn(URL2);
    jGroupsPeerInfoProvider.receive(message);

    when(message.getSrc()).thenReturn(ADDR3);
    when(message.getObject()).thenReturn(URL3);
    jGroupsPeerInfoProvider.receive(message);

    Set<PeerInfo> peers = jGroupsPeerInfoProvider.get();
    assertThat(peers.size()).isEqualTo(3);
    Iterator<PeerInfo> it = peers.iterator();
    assertThat(it.next().getDirectUrl()).isEqualTo(URL1);
    assertThat(it.next().getDirectUrl()).isEqualTo(URL2);
    assertThat(it.next().getDirectUrl()).isEqualTo(URL3);

    // remove one peer with address ADDR1 from the view
    List<Address> reducedView = new ArrayList<>();
    reducedView.add(ADDR2);
    reducedView.add(ADDR3);
    when(view.getMembers()).thenReturn(reducedView);
    when(view.size()).thenReturn(2);
    jGroupsPeerInfoProvider.setChannel(channel);
    jGroupsPeerInfoProvider.viewAccepted(view);
    peers = jGroupsPeerInfoProvider.get();
    assertThat(peers.size()).isEqualTo(2);
    it = peers.iterator();
    assertThat(it.next().getDirectUrl()).isEqualTo(URL2);
    assertThat(it.next().getDirectUrl()).isEqualTo(URL3);
  }

  @Test(expected = None.class)
  public void testViewAcceptedWithNoExceptionThrown() throws Exception {
    when(view.size()).thenReturn(3);
    jGroupsPeerInfoProvider.setChannel(channel);
    jGroupsPeerInfoProvider.viewAccepted(view);
  }

  @Test
  public void testViewAcceptedWhenPeerAddressIsNotNullAndIsNotMemberOfView() {
    when(view.getMembers()).thenReturn(members);
    when(view.size()).thenReturn(2);
    when(members.contains(peerAddress)).thenReturn(false);
    jGroupsPeerInfoProvider.addPeer(peerAddress, peerInfo);
    jGroupsPeerInfoProvider.setChannel(channel);
    jGroupsPeerInfoProvider.viewAccepted(view);

    assertThat(jGroupsPeerInfoProvider.getPeers()).isEmpty();
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet).isEmpty();
  }

  @Test
  public void testConnect() {
    jGroupsPeerInfoProvider.connect();
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet).isEmpty();
  }

  @Test
  public void testGetWhenPeerInfoIsOptionalEmpty() {
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet).isEmpty();
  }

  @Test
  public void testGetWhenPeerInfoIsPresent() {
    jGroupsPeerInfoProvider.addPeer(peerAddress, peerInfo);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    for (PeerInfo testPeerInfo : testPeerInfoSet) {
      assertThat(testPeerInfo.getDirectUrl()).contains("test message");
    }
    assertThat(testPeerInfoSet.size()).isEqualTo(1);
  }

  @Test
  public void testStop() throws Exception {
    jGroupsPeerInfoProvider.addPeer(peerAddress, peerInfo);
    jGroupsPeerInfoProvider.stop();
    assertThat(jGroupsPeerInfoProvider.getPeers().isEmpty());
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet).isEmpty();
  }
}
