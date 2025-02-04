// Copyright (C) 2019 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.forwarder.TestEvent;
import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.server.events.Event;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;

@TestPlugin(
    name = "high-availability",
    sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
    httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule")
public class RestForwarderServletModuleIT extends LightweightPluginDaemonTest {

  private final Event event = new TestEvent();
  private final String endpointPrefix = "/plugins/high-availability";
  private final String eventEndpointSuffix = "event";

  @Test
  @UseLocalDisk
  public void serveTypedEventEndpoint() throws Exception {
    adminRestSession
        .post(Joiner.on("/").join(endpointPrefix, eventEndpointSuffix, event.type), event)
        .assertStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  @UseLocalDisk
  public void doNotServeStraightEventEndpoint() throws Exception {
    adminRestSession
        .post(Joiner.on("/").join(endpointPrefix, eventEndpointSuffix), event)
        .assertMethodNotAllowed();
  }

  @Test
  @UseLocalDisk
  public void doNotServeStraightCacheEndpoint() throws Exception {
    adminRestSession.post(Joiner.on("/").join(endpointPrefix, "cache")).assertMethodNotAllowed();
  }
}
