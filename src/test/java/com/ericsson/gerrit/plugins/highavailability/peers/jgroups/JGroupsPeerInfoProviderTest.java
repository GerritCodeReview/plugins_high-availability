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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
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

  @Mock(answer = RETURNS_DEEP_STUBS)
  private MyUrlProvider myUrlProviderTest;

  private InetAddressFinder finder;

  private JGroupsPeerInfoProvider jGroupsPeerInfoProvider;

  @Before
  public void setUp() {
    finder = new InetAddressFinder(pluginConfigurationMock);
    jGroupsPeerInfoProvider =
        new JGroupsPeerInfoProvider(pluginConfigurationMock, finder, myUrlProviderTest);
  }

  @Test
  public void testRecieveWhenPeerAddressIsNull() throws Exception {
    Message message = new Message(null, "test message");
    Address sourceAddress = new IpAddress(8080);
    Address destAddress = new IpAddress("test.com");
    message.setSrc(sourceAddress);
    message.setDest(destAddress);
    jGroupsPeerInfoProvider.receive(message);
    assertThat(jGroupsPeerInfoProvider.getPeerAddress()).isEqualTo(sourceAddress);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    for (PeerInfo testPeerInfo : testPeerInfoSet) {
      assertThat(testPeerInfo.getDirectUrl()).contains("test message");
    }
    assertEquals(1, testPeerInfoSet.size());
  }

  @Test
  public void testReceiveWhenPeerAddressIsNotNull() throws Exception {
    Address expectedAddress = new IpAddress("checkAddress.com");
    Field field = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerAddress");
    field.setAccessible(true);
    field.set(jGroupsPeerInfoProvider, expectedAddress);

    Message message = new Message(null, "test message");
    Address sourceAddress = new IpAddress(8080);
    Address destAddress = new IpAddress("test.com");
    message.setSrc(sourceAddress);
    message.setDest(destAddress);
    jGroupsPeerInfoProvider.receive(message);
    assertThat(jGroupsPeerInfoProvider.getPeerAddress()).isEqualTo(expectedAddress);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertTrue(testPeerInfoSet.isEmpty());
    assertEquals(0, testPeerInfoSet.size());
  }

  @Test(expected = None.class)
  public void testViewAcceptedWithNoExceptionThrown() throws Exception {
    List<Address> members = new ArrayList<>();
    members.add(new IpAddress(3000));
    members.add(new IpAddress(3001));
    members.add(new IpAddress(3002));
    Address creator = new IpAddress(8080);
    View view = new View(creator, 1L, members);

    JChannel channel = new JChannel();
    channel.setName("testChannel");
    Field field = jGroupsPeerInfoProvider.getClass().getDeclaredField("channel");
    field.setAccessible(true);
    field.set(jGroupsPeerInfoProvider, channel);

    jGroupsPeerInfoProvider.viewAccepted(view);
  }

  @Test(expected = NullPointerException.class)
  public void testViewAcceptedWithExceptionThrown() throws Exception {
    List<Address> members = new ArrayList<>();
    members.add(new IpAddress(3000));
    members.add(new IpAddress(3001));
    Address creator = new IpAddress(8080);
    View view = new View(creator, 1L, members);

    jGroupsPeerInfoProvider.viewAccepted(view);
  }

  @Test
  public void testViewAcceptedWhenPeerAddressIsNotNullAndIsMemberOfView() throws Exception {
    List<Address> members = new ArrayList<>();
    members.add(new IpAddress(3000));
    members.add(new IpAddress(3001));
    Address creator = new IpAddress(8080);
    View view = new View(creator, 1L, members);
    Address peerAddress = new IpAddress(3002);
    Field peerAddressField = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerAddress");
    peerAddressField.setAccessible(true);
    peerAddressField.set(jGroupsPeerInfoProvider, peerAddress);

    Optional<PeerInfo> peerInfo = Optional.of(new PeerInfo("test message"));
    Field peerInfoField = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerInfo");
    peerInfoField.setAccessible(true);
    peerInfoField.set(jGroupsPeerInfoProvider, peerInfo);

    JChannel channel = new JChannel();
    channel.setName("testChannel");
    Field field = jGroupsPeerInfoProvider.getClass().getDeclaredField("channel");
    field.setAccessible(true);
    field.set(jGroupsPeerInfoProvider, channel);

    jGroupsPeerInfoProvider.viewAccepted(view);

    assertNull(jGroupsPeerInfoProvider.getPeerAddress());
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertTrue(testPeerInfoSet.isEmpty());
    assertEquals(0, testPeerInfoSet.size());
  }

  @Test
  public void testConnect() throws NoSuchFieldException, IllegalAccessException {
    jGroupsPeerInfoProvider.connect();
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertTrue(testPeerInfoSet.isEmpty());
    assertEquals(0, testPeerInfoSet.size());
  }

  @Test
  public void testGetWhenPeerInfoIsOptionalEmpty() {
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertTrue(testPeerInfoSet.isEmpty());
    assertEquals(0, testPeerInfoSet.size());
  }

  @Test
  public void testGetWhenPeerInfoIsPresent() throws NoSuchFieldException, IllegalAccessException {
    Optional<PeerInfo> peerInfo = Optional.of(new PeerInfo("test message"));
    Field peerInfoField = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerInfo");
    peerInfoField.setAccessible(true);
    peerInfoField.set(jGroupsPeerInfoProvider, peerInfo);

    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();

    for (PeerInfo testPeerInfo : testPeerInfoSet) {
      assertThat(testPeerInfo.getDirectUrl()).contains("test message");
    }
    assertEquals(1, testPeerInfoSet.size());
  }

  @Test
  public void testStop() throws Exception {
    Address testAddress = new IpAddress("checkAddress.com");
    Field field = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerAddress");
    field.setAccessible(true);
    field.set(jGroupsPeerInfoProvider, testAddress);

    Optional<PeerInfo> peerInfo = Optional.of(new PeerInfo("test message"));
    Field peerInfoField = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerInfo");
    peerInfoField.setAccessible(true);
    peerInfoField.set(jGroupsPeerInfoProvider, peerInfo);

    jGroupsPeerInfoProvider.stop();

    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertTrue(testPeerInfoSet.isEmpty());
    assertEquals(0, testPeerInfoSet.size());

    assertNull(jGroupsPeerInfoProvider.getPeerAddress());
  }
}
