// Copyright (C) 2021 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.AutoReindex;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexProjectHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.OneOffRequestContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectReindexRunnableTest {
  @Mock private ForwardedIndexProjectHandler indexer;
  @Mock private IndexTs indexTs;
  @Mock private OneOffRequestContext ctx;
  @Mock private ProjectCache projectCache;
  @Mock private Configuration cfg;
  @Mock private AutoReindex autoReindex;
  private ProjectReindexRunnable projectReindexRunnable;
  private Project.NameKey g;

  @Before
  public void setUp() throws Exception {
    Project.NameKey g = NameKey.parse("123");
    when(cfg.autoReindex()).thenReturn(autoReindex);
  }

  @Test
  public void projectIsIndexedWhenAutoProjectsReindexIsSet() throws Exception {
    when(autoReindex.autoProjectsReindex()).thenReturn(true);
    ProjectReindexRunnable projectReindexRunnable =
        new ProjectReindexRunnable(indexer, indexTs, ctx, projectCache, cfg);
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());

    Optional<Timestamp> projectLastTs = projectReindexRunnable.indexIfNeeded(g, currentTime);
    assertThat(projectLastTs.isPresent()).isTrue();
    assertThat(projectLastTs.get()).isEqualTo(currentTime);
    verify(indexer).index(g, Operation.INDEX, Optional.empty());
  }

  @Test
  public void projectIsNotIndexedWhenAutoProjectsReindexIsNotSet() throws Exception {
    when(autoReindex.autoProjectsReindex()).thenReturn(false);
    ProjectReindexRunnable projectReindexRunnable =
        new ProjectReindexRunnable(indexer, indexTs, ctx, projectCache, cfg);
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());

    Optional<Timestamp> projectLastTs = projectReindexRunnable.indexIfNeeded(g, currentTime);
    assertThat(projectLastTs.isPresent()).isFalse();
    verify(indexer, never()).index(g, Operation.INDEX, Optional.empty());
  }
}
