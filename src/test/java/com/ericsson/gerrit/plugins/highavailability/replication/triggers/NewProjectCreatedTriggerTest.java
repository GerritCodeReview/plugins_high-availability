// Copyright (C) 2020 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.replication.triggers;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedNewProjectCreatedEvent;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NewProjectCreatedTriggerTest {

  @Mock private ForwardedNewProjectCreatedEvent event;
  @Mock public NewProjectCreatedListener listener;

  private DynamicSet<NewProjectCreatedListener> listeners = DynamicSet.emptySet();
  private NewProjectCreatedTrigger triggerSpy;

  @Before
  public void setUp() {
    listeners.add(listener);
    NewProjectCreatedTrigger trigger = new NewProjectCreatedTrigger(listeners);
    triggerSpy = spy(trigger);
  }

  @Test
  public void testListenerMethodIsCalled() throws Exception {
    doReturn(Optional.of(listener)).when(triggerSpy).getReplicationListener();
    triggerSpy.triggerEvent(event);
    verify(listener).onNewProjectCreated(event);
  }

  @Test
  public void testListenerMissing() throws Exception {
    triggerSpy.triggerEvent(event);
    verify(listener, never()).onNewProjectCreated(event);
  }
}
