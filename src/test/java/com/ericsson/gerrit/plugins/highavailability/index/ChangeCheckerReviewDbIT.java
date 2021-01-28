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

package com.ericsson.gerrit.plugins.highavailability.index;

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.testing.NoteDbMode;
import java.util.Optional;
import org.junit.BeforeClass;
import org.junit.Test;

@TestPlugin(
    name = "high-availability",
    sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
    httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule")
public class ChangeCheckerReviewDbIT extends LightweightPluginDaemonTest {

  ChangeCheckerImpl.Factory changeCheckerFactory;

  @BeforeClass
  public static void setupTestSuite() {
    System.setProperty("gerrit.notedb", NoteDbMode.OFF.name());
  }

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();
    changeCheckerFactory = plugin.getSysInjector().getInstance(ChangeCheckerImpl.Factory.class);
  }

  @Test
  public void shouldNotPopulateMetaShaWhenReviewDb() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> eventOption = changeChecker.newIndexEvent();

    assertThat(eventOption.isPresent()).isTrue();
    IndexEvent event = eventOption.get();
    assertThat(event.metaSha).isNull();
  }

  @Test
  public void shouldReturnIsUpToDateTrueWhenEventContainsCorrectMetaAndTargetSha()
      throws Exception {
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
  public void shouldReturnFalseWhenTargetShaIsNotUpToDate() throws Exception {
    String testTargetRefSha = "abed47baf2818a86b68cf712073a748a6b5b293e";
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event =
        changeChecker
            .newIndexEvent()
            .map(
                e -> {
                  e.targetSha = testTargetRefSha;
                  return e;
                });

    assertThat(changeChecker.isChangeUpToDate(event)).isFalse();
  }
}
