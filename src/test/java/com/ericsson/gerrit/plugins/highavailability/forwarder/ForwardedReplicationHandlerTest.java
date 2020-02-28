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
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.GitReferenceUpdateTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.HeadUpdatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.NewProjectCreatedTrigger;
import com.ericsson.gerrit.plugins.highavailability.replication.triggers.ProjectDeletedTrigger;
import com.google.gerrit.extensions.events.ProjectEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedReplicationHandlerTest {

  @Mock private GitReferenceUpdateTrigger gitReferenceUpdateTrigger;
  @Mock private HeadUpdatedTrigger headUpdatedTrigger;
  @Mock private NewProjectCreatedTrigger newProjectCreatedTrigger;
  @Mock private ProjectDeletedTrigger projectDeletedTrigger;
  @Rule public ExpectedException exception = ExpectedException.none();
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
    ProjectEvent event = new ForwardedProjectDeletedEvent("project-name");
    handler.replicate(event);
    verify(projectDeletedTrigger).trigger((ForwardedProjectDeletedEvent) event);
  }
}
