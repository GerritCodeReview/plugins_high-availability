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
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import java.util.List;
import java.util.Optional;
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
  private Optional<PeerInfo> peerInfo;
  @Mock private JChannel channel;
  @Mock private MyUrlProvider myUrlProviderTest;
  @Mock private Message message;
  @Mock private Address peerAddress;
  @Mock private View view;
  @Mock private List<Address> members;

  @Before
  public void setUp() throws Exception {
    finder = new InetAddressFinder(pluginConfigurationMock);
    jGroupsPeerInfoProvider =
        new JGroupsPeerInfoProvider(pluginConfigurationMock, finder, myUrlProviderTest);
    peerInfo = Optional.of(new PeerInfo("test message"));
    channel.setName("testChannel");
  }

  @Test
  public void testRecieveWhenPeerAddressIsNull() {
    when(message.getSrc()).thenReturn(peerAddress);
    when(message.getObject()).thenReturn("test message");

    jGroupsPeerInfoProvider.receive(message);

    assertThat(jGroupsPeerInfoProvider.getPeerAddress()).isEqualTo(peerAddress);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    for (PeerInfo testPeerInfo : testPeerInfoSet) {
      assertThat(testPeerInfo.getDirectUrl()).contains("test message");
    }
    assertThat(testPeerInfoSet.size()).isEqualTo(1);
  }

  @Test
  public void testReceiveWhenPeerAddressIsNotNull() throws Exception {
    jGroupsPeerInfoProvider.setPeerAddress(new IpAddress("checkAddress.com"));

    jGroupsPeerInfoProvider.receive(message);

    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet.isEmpty()).isTrue();
    assertThat(testPeerInfoSet.size()).isEqualTo(0);
  }

  @Test(expected = None.class)
  public void testViewAcceptedWithNoExceptionThrown() throws Exception {
    when(view.getMembers()).thenReturn(members);
    when(view.size()).thenReturn(3);
    when(members.size()).thenReturn(3);
    jGroupsPeerInfoProvider.setChannel(channel);
    jGroupsPeerInfoProvider.viewAccepted(view);
  }

  @Test(expected = NullPointerException.class)
  public void testViewAcceptedWithExceptionThrown() throws Exception {
    when(view.getMembers()).thenReturn(members);
    when(view.size()).thenReturn(2);
    when(members.size()).thenReturn(2);
    jGroupsPeerInfoProvider.viewAccepted(view);
  }

  @Test
  public void testViewAcceptedWhenPeerAddressIsNotNullAndIsNotMemberOfView() {
    when(view.getMembers()).thenReturn(members);
    when(view.size()).thenReturn(2);
    when(members.size()).thenReturn(2);
    when(members.contains(peerAddress)).thenReturn(false);
    jGroupsPeerInfoProvider.setPeerAddress(peerAddress);
    jGroupsPeerInfoProvider.setPeerInfo(peerInfo);
    jGroupsPeerInfoProvider.setChannel(channel);
    jGroupsPeerInfoProvider.viewAccepted(view);

    assertThat(jGroupsPeerInfoProvider.getPeerAddress()).isEqualTo(null);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet.isEmpty()).isTrue();
    assertThat(testPeerInfoSet.size()).isEqualTo(0);
  }

  @Test
  public void testConnect() throws NoSuchFieldException, IllegalAccessException {
    jGroupsPeerInfoProvider.connect();
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet.isEmpty()).isTrue();
    assertThat(testPeerInfoSet.size()).isEqualTo(0);
  }

  @Test
  public void testGetWhenPeerInfoIsOptionalEmpty() {
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet.isEmpty()).isTrue();
    assertThat(testPeerInfoSet.size()).isEqualTo(0);
  }

  @Test
  public void testGetWhenPeerInfoIsPresent() {
    jGroupsPeerInfoProvider.setPeerInfo(peerInfo);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    for (PeerInfo testPeerInfo : testPeerInfoSet) {
      assertThat(testPeerInfo.getDirectUrl()).contains("test message");
    }
    assertThat(testPeerInfoSet.size()).isEqualTo(1);
  }

  @Test
  public void testStop() throws Exception {
    jGroupsPeerInfoProvider.setPeerAddress(peerAddress);
    jGroupsPeerInfoProvider.setPeerInfo(peerInfo);
    jGroupsPeerInfoProvider.stop();
    assertThat(jGroupsPeerInfoProvider.getPeerAddress()).isEqualTo(null);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertThat(testPeerInfoSet.isEmpty()).isTrue();
    assertThat(testPeerInfoSet.size()).isEqualTo(0);
  }
}
