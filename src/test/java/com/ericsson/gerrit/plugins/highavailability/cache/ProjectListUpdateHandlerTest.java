// Copyright (C) 2018 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectListUpdateHandlerTest {
  private ProjectListUpdateHandler handler;

  @Mock private Forwarder forwarder;

  @Before
  public void setUp() {
    handler = new ProjectListUpdateHandler(forwarder);
  }

  @Test
  public void shouldForwardAddedProject() throws Exception {
    String projectName = "projectToAdd";
    NewProjectCreatedListener.Event event = mock(NewProjectCreatedListener.Event.class);
    when(event.getProjectName()).thenReturn(projectName);
    handler.onNewProjectCreated(event);
    verify(forwarder).addToProjectList(projectName);
  }

  @Test
  public void shouldForwardDeletedProject() throws Exception {
    String projectName = "projectToDelete";
    ProjectDeletedListener.Event event = mock(ProjectDeletedListener.Event.class);
    when(event.getProjectName()).thenReturn(projectName);
    handler.onProjectDeleted(event);
    verify(forwarder).removeFromProjectList(projectName);
  }

  @Test
  public void shouldNotForwardIfAlreadyForwardedEvent() throws Exception {
    Context.setForwardedEvent(true);
    handler.onNewProjectCreated(mock(NewProjectCreatedListener.Event.class));
    handler.onProjectDeleted(mock(ProjectDeletedListener.Event.class));
    Context.unsetForwardedEvent();
    verifyNoInteractions(forwarder);
  }
}
