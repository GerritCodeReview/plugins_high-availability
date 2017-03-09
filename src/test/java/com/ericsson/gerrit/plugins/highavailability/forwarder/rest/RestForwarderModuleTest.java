// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;

import org.junit.Test;

public class RestForwarderModuleTest {

  @Test
  public void testForwardUrlProvider() {
    PeerInfo peerInfo = mock(PeerInfo.class);
    String expected = "someUrl";
    when(peerInfo.getDirectUrl()).thenReturn(expected);
    RestForwarderModule module = new RestForwarderModule();
    assertThat(module.forwardUrl(peerInfo)).isEqualTo(expected);
  }
}
