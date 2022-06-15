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
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import java.lang.reflect.Field;
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

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Message message;

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Address peerAddress;

  @Mock(answer = RETURNS_DEEP_STUBS)
  private View view;

  @Mock(answer = RETURNS_DEEP_STUBS)
  private List<Address> members;

  private InetAddressFinder finder;
  private JGroupsPeerInfoProvider jGroupsPeerInfoProvider;
  private Optional<PeerInfo> peerInfo;
  private JChannel channel;

  @Before
  public void setUp() throws Exception {
    finder = new InetAddressFinder(pluginConfigurationMock);
    jGroupsPeerInfoProvider =
        new JGroupsPeerInfoProvider(pluginConfigurationMock, finder, myUrlProviderTest);
    peerInfo = Optional.of(new PeerInfo("test message"));
    channel = new JChannel();
    channel.setName("testChannel");
  }

  @Test
  public void testRecieveWhenPeerAddressIsNull() {
    when(message.getSrc()).thenReturn(peerAddress);
    when(message.getObject()).thenReturn("test message");
    jGroupsPeerInfoProvider.receive(message);
    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    for (PeerInfo testPeerInfo : testPeerInfoSet) {
      assertThat(testPeerInfo.getDirectUrl()).contains("test message");
    }
    assertEquals(1, testPeerInfoSet.size());
  }

  @Test
  public void testReceiveWhenPeerAddressIsNotNull() throws Exception {
    Field field = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerAddress");
    field.setAccessible(true);
    field.set(jGroupsPeerInfoProvider, new IpAddress("checkAddress.com"));

    jGroupsPeerInfoProvider.receive(message);

    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertTrue(testPeerInfoSet.isEmpty());
    assertEquals(0, testPeerInfoSet.size());
  }

  @Test(expected = None.class)
  public void testViewAcceptedWithNoExceptionThrown() throws Exception {
    when(view.getMembers()).thenReturn(members);
    when(view.size()).thenReturn(3);
    when(members.size()).thenReturn(3);

    Field field = jGroupsPeerInfoProvider.getClass().getDeclaredField("channel");
    field.setAccessible(true);
    field.set(jGroupsPeerInfoProvider, channel);

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
  public void testViewAcceptedWhenPeerAddressIsNotNullAndIsNotMemberOfView() throws Exception {
    when(view.getMembers()).thenReturn(members);
    when(view.size()).thenReturn(2);
    when(members.size()).thenReturn(2);
    when(members.contains(peerAddress)).thenReturn(false);

    Field peerAddressField = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerAddress");
    peerAddressField.setAccessible(true);
    peerAddressField.set(jGroupsPeerInfoProvider, peerAddress);

    Field peerInfoField = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerInfo");
    peerInfoField.setAccessible(true);
    peerInfoField.set(jGroupsPeerInfoProvider, peerInfo);

    Field field = jGroupsPeerInfoProvider.getClass().getDeclaredField("channel");
    field.setAccessible(true);
    field.set(jGroupsPeerInfoProvider, channel);

    jGroupsPeerInfoProvider.viewAccepted(view);

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
    Field peerInfoField = jGroupsPeerInfoProvider.getClass().getDeclaredField("peerInfo");
    peerInfoField.setAccessible(true);
    peerInfoField.set(jGroupsPeerInfoProvider, peerInfo);

    jGroupsPeerInfoProvider.stop();

    Set<PeerInfo> testPeerInfoSet = jGroupsPeerInfoProvider.get();
    assertTrue(testPeerInfoSet.isEmpty());
    assertEquals(0, testPeerInfoSet.size());
  }
}
