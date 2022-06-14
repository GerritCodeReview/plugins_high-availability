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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.collect.ImmutableList;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InetAddressFinderTest {

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Configuration configuration;

  @Mock private NetworkInterface mockInterface;

  private InetAddressFinder finder;
  private List<NetworkInterface> testNetworkInterfaces;

  @Before
  public void setUp() {
    finder = new InetAddressFinder(configuration);
    testNetworkInterfaces = new ArrayList<>();
  }

  @Test
  public void testNoSuitableInterfaceWhenFindFirstAppropriateAddress() throws SocketException {
    when(mockInterface.isLoopback()).thenReturn(true);
    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of("mockInterface1"));
    testNetworkInterfaces.add(mockInterface);
    assertThat(finder.findFirstAppropriateAddress(testNetworkInterfaces).isPresent()).isFalse();
  }

  @Test
  public void testOptionalEmptyIsReturnedWhenFindFirstAppropriateAddress() throws SocketException {
    when(mockInterface.isLoopback()).thenReturn(false);
    when(mockInterface.isUp()).thenReturn(true);
    when(mockInterface.supportsMulticast()).thenReturn(true);
    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of());
    testNetworkInterfaces.add(mockInterface);
    Enumeration mockInetAddresses = mock(Enumeration.class);

    when(mockInterface.getInetAddresses()).thenReturn(mockInetAddresses);
    assertThat(finder.findFirstAppropriateAddress(testNetworkInterfaces))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void testInet6AddressIsReturnedWhenFindFirstAppropriateAddress() throws SocketException {
    when(mockInterface.isLoopback()).thenReturn(false);
    when(mockInterface.isUp()).thenReturn(true);
    when(mockInterface.supportsMulticast()).thenReturn(true);
    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of());
    testNetworkInterfaces.add(mockInterface);
    Inet6Address mockInet6Address = mock(Inet6Address.class);
    List<Inet6Address> mocklist = new ArrayList<>();
    mocklist.add(mockInet6Address);
    Enumeration mockInetAddresses = Collections.enumeration(mocklist);

    when(mockInterface.getInetAddresses()).thenReturn(mockInetAddresses);
    assertThat(finder.findFirstAppropriateAddress(testNetworkInterfaces))
        .isEqualTo(Optional.of(mockInet6Address));
  }

  @Test
  public void testInet4AddressIsReturnedWhenFindFirstAppropriateAddress() throws SocketException {
    when(mockInterface.isLoopback()).thenReturn(false);
    when(mockInterface.isUp()).thenReturn(true);
    when(mockInterface.supportsMulticast()).thenReturn(true);
    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of());
    System.setProperty("java.net.preferIPv4Stack", "true");
    testNetworkInterfaces.add(mockInterface);
    Inet4Address mockInet4Address = mock(Inet4Address.class);
    List<Inet4Address> mocklist = new ArrayList<>();
    mocklist.add(mockInet4Address);

    Enumeration mockInetAddresses = Collections.enumeration(mocklist);
    when(mockInterface.getInetAddresses()).thenReturn(mockInetAddresses);

    finder = new InetAddressFinder(configuration);
    assertThat(finder.findFirstAppropriateAddress(testNetworkInterfaces))
        .isEqualTo(Optional.of(mockInet4Address));
  }

  @Test
  public void testNoSkipWhenEmptySkipList() {
    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of());
    assertThat(finder.shouldSkip("foo")).isFalse();
    assertThat(finder.shouldSkip("bar")).isFalse();
  }

  @Test
  public void testSkipByName() {
    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of("foo"));
    assertThat(finder.shouldSkip("foo")).isTrue();
    assertThat(finder.shouldSkip("bar")).isFalse();

    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of("foo", "bar"));
    assertThat(finder.shouldSkip("foo")).isTrue();
    assertThat(finder.shouldSkip("bar")).isTrue();
  }

  @Test
  public void testSkipByWildcard() {
    when(configuration.jgroups().skipInterface()).thenReturn(ImmutableList.of("foo*"));
    assertThat(finder.shouldSkip("foo")).isTrue();
    assertThat(finder.shouldSkip("foo1")).isTrue();
    assertThat(finder.shouldSkip("bar")).isFalse();
  }
}
