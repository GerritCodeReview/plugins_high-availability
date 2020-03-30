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

package com.ericsson.gerrit.plugins.highavailability.replication;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedGitReferenceUpdatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedHeadUpdatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedNewProjectCreatedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedProjectDeletedEvent;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener.Event;
import com.google.gerrit.extensions.events.HeadUpdatedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReplicationListenerTest {
  private static final String PLUGIN_NAME = "high-availability";

  private ReplicationListener replicationListener;

  @Mock private Forwarder forwarder;

  @Before
  public void setUp() {
    replicationListener =
        new ReplicationListener(forwarder, MoreExecutors.directExecutor(), PLUGIN_NAME);
  }

  @Test
  public void shouldForwardGitReferenceUpdatedListenerEvent() throws Exception {
    GitReferenceUpdatedListener.Event event = mock(GitReferenceUpdatedListener.Event.class);
    replicationListener.onGitReferenceUpdated(event);
    verify(forwarder).replicate(event, ForwardedGitReferenceUpdatedEvent.class.getName());
  }

  @Test
  public void shouldForwardNewProjectCreatedListenerEvent() throws Exception {
    NewProjectCreatedListener.Event event = mock(NewProjectCreatedListener.Event.class);
    replicationListener.onNewProjectCreated(event);
    verify(forwarder).replicate(event, ForwardedNewProjectCreatedEvent.class.getName());
  }

  @Test
  public void shouldForwardProjectDeletedListenerEvent() throws Exception {
    ProjectDeletedListener.Event event = mock(ProjectDeletedListener.Event.class);
    when(event.getProjectName()).thenReturn("testProject");
    replicationListener.onProjectDeleted(event);
    ArgumentCaptor<ForwardedProjectDeletedEvent> argumentCaptor =
        ArgumentCaptor.forClass(ForwardedProjectDeletedEvent.class);
    verify(forwarder)
        .replicate(argumentCaptor.capture(), eq(ForwardedProjectDeletedEvent.class.getName()));
    assertThat(argumentCaptor.getValue()).isNotNull();
  }

  @Test
  public void shouldForwardHeadUpdatedListenerEvent() throws Exception {
    HeadUpdatedListener.Event event = mock(HeadUpdatedListener.Event.class);
    replicationListener.onHeadUpdated(event);
    verify(forwarder).replicate(event, ForwardedHeadUpdatedEvent.class.getName());
  }

  @Test
  public void shouldNotForwardIfAlreadyForwardedEvent() throws Exception {
    replicationListener.onGitReferenceUpdated(mock(ForwardedGitReferenceUpdatedEvent.class));
    replicationListener.onNewProjectCreated(mock(ForwardedNewProjectCreatedEvent.class));
    replicationListener.onProjectDeleted(mock(ForwardedProjectDeletedEvent.class));
    replicationListener.onHeadUpdated(mock(ForwardedHeadUpdatedEvent.class));
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void testEventTaskToString() throws Exception {
    GitReferenceUpdatedListener.Event event = mock(GitReferenceUpdatedListener.Event.class);
    ReplicationListener.ReplicationTask<Event> task =
        replicationListener.new ReplicationTask<>(event, "classname");
    assertThat(task.toString())
        .isEqualTo(
            String.format(
                "[%s] Trigger replication of project '%s' (%s)",
                PLUGIN_NAME, event.getProjectName(), "classname"));
  }
}
