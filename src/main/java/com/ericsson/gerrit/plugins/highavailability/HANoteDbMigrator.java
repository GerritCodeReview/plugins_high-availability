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

package com.ericsson.gerrit.plugins.highavailability;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

public class HANoteDbMigrator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final Provider<NoteDbMigrator.Builder> migratorBuilderProvider;
  private final NotesMigration migration;
  private final GitRepositoryManager repoManager;
  private boolean trial;

  @Inject
  public HANoteDbMigrator(
      Provider<NoteDbMigrator.Builder> migratorBuilderProvider,
      @GerritServerConfig Config cfg,
      NotesMigration migration,
      GitRepositoryManager repoManager) {
    this.migratorBuilderProvider = migratorBuilderProvider;
    this.migration = migration;
    this.repoManager = repoManager;
    this.trial = NoteDbMigrator.getTrialMode(cfg);
  }

  public void migrate(Change.Id id, NameKey project) {
    if (migration.readChanges()
        && !migration.disableChangeReviewDb()
        && trial
        && !metaRefExists(id, project)) {
      try (NoteDbMigrator migrator =
          migratorBuilderProvider
              .get()
              .setThreads(1)
              .setAutoMigrate(true)
              .setTrialMode(trial)
              .setChanges(ImmutableSet.of(id))
              .build()) {
        migrator.rebuild();
      } catch (Exception e) {
        logger.atSevere().withCause(e).log(
            String.format("Error in online NoteDb migration for change:%s", id));
      }
    }
  }

  private boolean metaRefExists(Id id, NameKey project) {
    try (Repository repo = repoManager.openRepository(project)) {
      String metaRef = RefNames.changeMetaRef(id);
      return repo.getRefDatabase().exactRef(metaRef) != null;
    } catch (RepositoryNotFoundException e) {
      logger.atSevere().withCause(e).log(
          String.format(
              "Error in online NoteDb migration for change:%s ,unable to open project: %s",
              id, project));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          String.format("Error in online NoteDb migration for change:%s, project %s", id, project));
    }
    return false;
  }
}
