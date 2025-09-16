// Copyright (C) 2022 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.index;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.OneOffRequestContext;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ChangeCheckerImplTest {

  @Mock private GitRepositoryManager gitRepoMgr;
  @Mock private ChangeFinder changeFinder;
  @Mock private OneOffRequestContext oneOffReqCtx;
  @Mock private ChangeNotes testChangeNotes;
  @Mock private Change testChange;

  private final Instant testLastUpdatedOn = Instant.now();
  private final String changeId = "1";
  Optional<IndexEvent> event = Optional.empty();
  private Optional<Instant> computedChangeTs = Optional.empty();
  private ChangeCheckerImpl changeChecker;

  @Before
  public void setUp() {
    changeChecker = new ChangeCheckerImpl(gitRepoMgr, changeFinder, oneOffReqCtx, changeId);
  }

  @Test
  public void testGetChangeNotes() {
    when(changeFinder.findOne(changeId)).thenReturn(Optional.of(testChangeNotes));
    assertThat(changeChecker.getChangeNotes()).isEqualTo(Optional.of(testChangeNotes));
  }

  @Test
  public void testGetComputedChangeTs() {
    computedChangeTs = Optional.of(testLastUpdatedOn);
    when(changeChecker.getChangeNotes()).thenReturn(Optional.of(testChangeNotes));
    when(testChangeNotes.getChange()).thenReturn(testChange);
    when(testChange.getLastUpdatedOn()).thenReturn(testLastUpdatedOn);
    assertThat(changeChecker.getComputedChangeTs()).isEqualTo(computedChangeTs);
  }

  @Test
  public void testNewIndexEventWhenChangeTimeStampIsEmpty() throws IOException {
    assertThat(changeChecker.newIndexEvent().isPresent()).isFalse();
  }

  @Test
  public void testIsChangeUpToDateWhenComputedChangeTsIsNotPresent() throws IOException {
    assertThat(changeChecker.isChangeUpToDate(event)).isFalse();
  }
}
