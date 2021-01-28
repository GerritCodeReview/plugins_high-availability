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

package com.ericsson.gerrit.plugins.highavailability.index;

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.BeforeClass;
import org.junit.Test;

@UseLocalDisk
@NoHttpd
@TestPlugin(
    name = "high-availability",
    sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
    httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule")
public class ChangeCheckerIT extends LightweightPluginDaemonTest {

  private static final String TEST_META_REF_SHA = "6212efebe6e8b9f439a8ad013243e602afab7441";
  private static final String TEST_TARGET_REF_SHA = "abed47baf2818a86b68cf712073a748a6b5b293e";

  ChangeCheckerImpl.Factory changeCheckerFactory;

  @BeforeClass
  public static void setupTestSuite() {
    System.setProperty("gerrit.notedb", NoteDbMode.PRIMARY.name());
  }

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();
    changeCheckerFactory = plugin.getSysInjector().getInstance(ChangeCheckerImpl.Factory.class);
  }

  @Test
  public void shouldPopulateMetaShaWhenNoteDb() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> eventOption = changeChecker.newIndexEvent();

    assertThat(eventOption.get()).isNotNull();
    IndexEvent event = eventOption.get();
    assertThat(event.metaSha).isNotNull();
    assertThat(event.metaSha).isEqualTo(readMetaSha(change));
  }

  @Test
  public void shouldReturnIsUpToDateTrue() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event = changeChecker.newIndexEvent();

    assertThat(changeChecker.isChangeUpToDate(event)).isTrue();
  }

  @Test
  public void shouldReturnIsUpToDateTrueWhenMetaShaIsNull() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event =
        changeChecker
            .newIndexEvent()
            .map(
                e -> {
                  e.metaSha = null;
                  return e;
                });

    assertThat(changeChecker.isChangeUpToDate(event)).isTrue();
  }

  @Test
  public void shouldReturnIsUpToDateTrueWhenTargetShaIsNull() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event =
        changeChecker
            .newIndexEvent()
            .map(
                e -> {
                  e.targetSha = null;
                  return e;
                });

    assertThat(changeChecker.isChangeUpToDate(event)).isTrue();
  }

  @Test
  public void shouldReturnFalseWhenMetaShaIsNotUpToData() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event =
        changeChecker
            .newIndexEvent()
            .map(
                e -> {
                  e.metaSha = TEST_META_REF_SHA;
                  return e;
                });

    assertThat(changeChecker.isChangeUpToDate(event)).isFalse();
  }

  @Test
  public void shouldReturnFalseWhenTargetShaIsNotUpToData() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event =
        changeChecker
            .newIndexEvent()
            .map(
                e -> {
                  e.targetSha = TEST_TARGET_REF_SHA;
                  return e;
                });

    assertThat(changeChecker.isChangeUpToDate(event)).isFalse();
  }

  private String readMetaSha(Result change) throws IOException, OrmException {
    try (Repository repo = repoManager.openRepository(change.getChange().change().getProject())) {
      String refName = RefNames.changeMetaRef(change.getChange().getId());
      Ref ref = repo.exactRef(refName);
      if (ref == null) {
        return null;
      }

      return ref.getTarget().getObjectId().getName();
    }
  }
}
