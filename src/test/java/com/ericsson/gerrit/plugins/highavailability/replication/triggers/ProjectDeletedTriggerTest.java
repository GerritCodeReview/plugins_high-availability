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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedProjectDeletedEvent;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectDeletedTriggerTest {
  @Mock private ForwardedProjectDeletedEvent event;
  @Mock public ProjectDeletedListener listener;

  private DynamicSet<ProjectDeletedListener> listeners = DynamicSet.emptySet();
  private ProjectDeletedTrigger trigger;
  private ProjectDeletedTrigger triggerSpy;

  @Before
  public void setUp() {
    listeners.add(listener);
    trigger = new ProjectDeletedTrigger(listeners);
    triggerSpy = spy(trigger);
    doReturn(listener).when(triggerSpy).getListener();
  }

  @Test
  public void listenerMethodIsCalledTest() throws Exception {
    triggerSpy.trigger(event);
    verify(listener).onProjectDeleted(event);
  }
}
