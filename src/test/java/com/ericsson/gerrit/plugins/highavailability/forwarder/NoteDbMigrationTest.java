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
//
package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.MutableNotesMigration;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.rebuild.MigrationException;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NoteDbMigrationTest {
  //	private final Answer<Object> SELF_RETURNING = new SelfReturningAnswer();
  @Mock Provider<NoteDbMigrator.Builder> migratorBuilderProvider;

  @Mock(answer = Answers.RETURNS_SELF)
  NoteDbMigrator.Builder migratorBuilder;

  @Mock Config cfg;
  @Mock GitRepositoryManager repoManager;
  @Mock Repository repository;
  @Mock RefDatabase refDatabase;
  @Mock Ref ref;
  @Mock NoteDbMigrator noteDbMigrator;

  NotesMigration migration;
  Change.Id id = new Change.Id(1);
  Project.NameKey project = new Project.NameKey("test_project");
  NoteDbMigration objectUnderTest;

  @Before
  public void setUp() throws Exception {
    when(migratorBuilderProvider.get()).thenReturn(migratorBuilder);
    when(migratorBuilder.build()).thenReturn(noteDbMigrator);
    when(repoManager.openRepository(any())).thenReturn(repository);
    when(repository.getRefDatabase()).thenReturn(refDatabase);
    when(cfg.getBoolean(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(true);
    when(refDatabase.exactRef(anyString())).thenReturn(null);

    migration =
        MutableNotesMigration.newDisabled().setReadChanges(true).setDisableChangeReviewDb(false);

    objectUnderTest = new NoteDbMigration(migratorBuilderProvider, cfg, migration, repoManager);
  }

  @Test
  public void shouldMigrateChange() throws MigrationException, OrmException {
    objectUnderTest.migrate(id, project);
    verify(noteDbMigrator, times(1)).rebuild();
  }

  @Test
  public void shouldSkipMigrationWhenMetaRefExists() throws OrmException, IOException {
    when(refDatabase.exactRef(anyString())).thenReturn(ref);
    objectUnderTest.migrate(id, project);
    verify(noteDbMigrator, never()).rebuild();
  }

  @Test
  public void shouldSkipMigrationWhenReviewDbIsDisabled() throws OrmException, IOException {
    migration =
        MutableNotesMigration.newDisabled()
            .setReadChanges(true)
            .setChangePrimaryStorage(PrimaryStorage.NOTE_DB)
            .setDisableChangeReviewDb(true);
    objectUnderTest = new NoteDbMigration(migratorBuilderProvider, cfg, migration, repoManager);

    objectUnderTest.migrate(id, project);
    verify(noteDbMigrator, never()).rebuild();
  }

  @Test
  public void shouldSkipMigrationWhenReadChangesIsFalse() throws OrmException, IOException {
    migration =
        MutableNotesMigration.newDisabled().setReadChanges(false).setDisableChangeReviewDb(false);
    objectUnderTest = new NoteDbMigration(migratorBuilderProvider, cfg, migration, repoManager);

    objectUnderTest.migrate(id, project);
    verify(noteDbMigrator, never()).rebuild();
  }

  @Test
  public void shouldSkipMigrationWhenNotInTrialMode() throws OrmException, IOException {
    when(cfg.getBoolean(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(false);
    objectUnderTest = new NoteDbMigration(migratorBuilderProvider, cfg, migration, repoManager);

    objectUnderTest.migrate(id, project);
    verify(noteDbMigrator, never()).rebuild();
  }
}
