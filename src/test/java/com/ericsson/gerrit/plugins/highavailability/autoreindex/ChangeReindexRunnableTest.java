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

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.OneOffRequestContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ChangeReindexRunnableTest {

  @Mock private ForwardedIndexChangeHandler indexer;
  @Mock private IndexTs indexTs;
  @Mock private OneOffRequestContext ctx;
  @Mock private ProjectCache projectCache;
  @Mock private Groups groups;
  @Mock private GitRepositoryManager repoManager;
  @Mock private AllUsersName allUsers;
  @Mock private ChangeNotes.Factory changeNotesFactory;
  @Mock private Repository repo;
  @Mock private ChangeNotesResult changeNotesRes;
  @Mock private ChangeNotes changeNotes;

  private ChangeReindexRunnable changeReindexRunnable;

  @Before
  public void setUp() throws Exception {
    changeReindexRunnable =
        new ChangeReindexRunnable(
            indexer, indexTs, ctx, projectCache, repoManager, changeNotesFactory);
  }

  @Test
  public void changeIsIndexedWhenItIsCreatedAfterLastChangeReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp afterCurrentTime = new Timestamp(currentTime.getTime() + 1000L);
    Change change = newChange(afterCurrentTime);

    Optional<Timestamp> changeLastTs = changeReindexRunnable.indexIfNeeded(change, currentTime);
    assertThat(changeLastTs.isPresent()).isTrue();
    assertThat(changeLastTs.get()).isEqualTo(afterCurrentTime);
    verify(indexer).index(changeProjectIndexKey(change), Operation.INDEX, Optional.empty());
  }

  @Test
  public void changeIsIndexedDuringRun() throws Exception {
    LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);
    Timestamp afterCurrentTime =
        new Timestamp(currentTime.toEpochSecond(ZoneOffset.UTC) * 1000 + 1000L);
    Change change = newChange(afterCurrentTime);

    when(indexTs.getUpdateTs(AbstractIndexRestApiServlet.IndexName.CHANGE))
        .thenReturn(Optional.of(currentTime));
    when(projectCache.all()).thenReturn(ImmutableSortedSet.of(change.getProject()));
    when(repoManager.openRepository(change.getProject())).thenReturn(repo);
    when(changeNotesFactory.scan(repo, change.getProject())).thenReturn(Stream.of(changeNotesRes));
    when(changeNotesRes.error()).thenReturn(Optional.empty());
    when(changeNotesRes.notes()).thenReturn(changeNotes);
    when(changeNotes.getChange()).thenReturn(change);

    changeReindexRunnable.run();

    verify(indexer).index(changeProjectIndexKey(change), Operation.INDEX, Optional.empty());
  }

  @Test
  public void groupIsNotIndexedWhenItIsCreatedBeforeLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp beforeCurrentTime = new Timestamp(currentTime.getTime() - 1000L);
    Change change = newChange(beforeCurrentTime);

    Optional<Timestamp> changeLastTs = changeReindexRunnable.indexIfNeeded(change, currentTime);
    assertThat(changeLastTs.isPresent()).isFalse();
    verify(indexer, never())
        .index(changeProjectIndexKey(change), Operation.INDEX, Optional.empty());
  }

  private String changeProjectIndexKey(Change change) {
    return change.getProject() + "~" + change.getChangeId();
  }

  private Change newChange(Timestamp changeTs) {
    return new Change(
        Change.key("changekey"),
        Change.id(123),
        Account.id(1000000),
        BranchNameKey.create("projectname", "main"),
        changeTs);
  }
}
