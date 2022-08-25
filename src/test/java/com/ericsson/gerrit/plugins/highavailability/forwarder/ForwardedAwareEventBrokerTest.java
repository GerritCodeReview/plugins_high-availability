// Copyright (C) 2016 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import org.junit.Before;
import org.junit.Test;

public class ForwardedAwareEventBrokerTest {

  private EventListener listenerMock;
  private AllowedEventListener allowListenerMock;
  private ForwardedAwareEventBroker broker;
  private ForwardedAwareEventBroker brokerWithGerritInstanceId;
  private Event event;
  private String gerritInstanceId = "gerrit-instance-id";

  @Before
  public void setUp() {
    PluginMetrics mockMetrics = mock(PluginMetrics.class);
    listenerMock = mock(EventListener.class);
    allowListenerMock = mock(AllowedEventListener.class);
    DynamicSet<EventListener> set = DynamicSet.emptySet();
    set.add("high-availability", listenerMock);
    event = new TestEvent();
    PluginSetContext<EventListener> listeners = new PluginSetContext<>(set, mockMetrics);
    broker =
        new ForwardedAwareEventBroker(null, listeners, null, null, null, null, allowListenerMock);
    brokerWithGerritInstanceId =
        new ForwardedAwareEventBroker(
            null, listeners, null, null, null, gerritInstanceId, allowListenerMock);
  }

  @Test
  public void shouldDispatchEvent() {
    broker.fireEventForUnrestrictedListeners(event);
    verify(listenerMock).onEvent(event);
  }

  @Test
  public void shouldNotDispatchForwardedEvents() {
    Context.setForwardedEvent(true);
    try {
      broker.fireEventForUnrestrictedListeners(event);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyZeroInteractions(listenerMock);
  }

  @Test
  public void shouldNotDispatchEventWhenEventInstanceIdIsDefinedButGerritInstanceIdIsNot() {
    event.instanceId = gerritInstanceId;
    try {
      broker.fireEventForUnrestrictedListeners(event);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyZeroInteractions(listenerMock);
  }

  @Test
  public void shouldNotDispatchEventWhenGerritInstanceIdIsDefinedButEventInstanceIdIsNot() {
    try {
      brokerWithGerritInstanceId.fireEventForUnrestrictedListeners(event);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyZeroInteractions(listenerMock);
  }

  @Test
  public void shouldNotDispatchEventWhenInstanceIdsAreDifferent() {
    event.instanceId = "some-other-gerrit-instance-id";
    try {
      brokerWithGerritInstanceId.fireEventForUnrestrictedListeners(event);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyZeroInteractions(listenerMock);
  }

  @Test
  public void shouldDispatchEventWhenInstanceIdsAreDifferentToAllowedListener() {
    event.instanceId = "some-other-gerrit-instance-id";
    when(allowListenerMock.isAllowed(any())).thenReturn(true);
    try {
      brokerWithGerritInstanceId.fireEventForUnrestrictedListeners(event);
    } finally {
      Context.unsetForwardedEvent();
    }
    verify(listenerMock).onEvent(event);
  }

  @Test
  public void shouldDispatchEventWhenInstanceIdsAreEqual() {
    event.instanceId = gerritInstanceId;
    brokerWithGerritInstanceId.fireEventForUnrestrictedListeners(event);
    verify(listenerMock).onEvent(event);
  }
}
