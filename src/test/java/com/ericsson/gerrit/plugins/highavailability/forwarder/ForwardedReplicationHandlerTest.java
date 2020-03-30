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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static org.mockito.Mockito.verify;

import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedProjectDeletedEvent;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.GitReferenceUpdatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.HeadUpdatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.NewProjectCreatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.ProjectDeletedTrigger;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.ProjectEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedReplicationHandlerTest {

  @Mock private GitReferenceUpdatedTrigger gitReferenceUpdateTrigger;
  @Mock private HeadUpdatedTrigger headUpdatedTrigger;
  @Mock private NewProjectCreatedTrigger newProjectCreatedTrigger;
  @Mock private ProjectDeletedTrigger projectDeletedTrigger;

  private ForwardedReplicationHandler handler;

  @Before
  public void setUp() throws Exception {
    handler =
        new ForwardedReplicationHandler(
            gitReferenceUpdateTrigger,
            headUpdatedTrigger,
            newProjectCreatedTrigger,
            projectDeletedTrigger);
  }

  @Test
  public void testSuccessfulReplicationTrigger() throws Exception {
    ForwardedProjectDeletedEvent event = new ForwardedProjectDeletedEvent("project-name");
    handler.replicate(event);
    verify(projectDeletedTrigger).triggerEvent(event);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnsupportedProjectEvent() throws Exception {
    ProjectEvent event =
        new ProjectEvent() {
          @Override
          public String getProjectName() {
            return "unsupported";
          }

          @Override
          public NotifyHandling getNotify() {
            return NotifyHandling.NONE;
          }
        };
    handler.replicate(event);
  }
}
