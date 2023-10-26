// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventHandlerTest {
  private EventHandler eventHandler;

  @Mock private Forwarder forwarder;

  @Before
  public void setUp() {
    eventHandler = new EventHandler(forwarder);
  }

  @Test
  public void shouldForwardAnyProjectEvent() throws Exception {
    Event event = mock(ProjectEvent.class);
    eventHandler.onEvent(event);
    verify(forwarder).send(event);
  }

  @Test
  public void shouldNotForwardNonProjectEvent() throws Exception {
    eventHandler.onEvent(mock(Event.class));
    verifyNoInteractions(forwarder);
  }

  @Test
  public void shouldNotForwardIfAlreadyForwardedEvent() throws Exception {
    Context.setForwardedEvent(true);
    eventHandler.onEvent(mock(ProjectEvent.class));
    Context.unsetForwardedEvent();
    verifyNoInteractions(forwarder);
  }
}
