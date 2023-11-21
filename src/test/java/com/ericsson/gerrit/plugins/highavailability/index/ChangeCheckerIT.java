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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.RefNames;
import java.io.IOException;
import java.util.Optional;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.Comment;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@TestPlugin(
    name = "high-availability",
    sysModule = "com.ericsson.gerrit.plugins.highavailability.Module",
    httpModule = "com.ericsson.gerrit.plugins.highavailability.HttpModule")
public class ChangeCheckerIT extends LightweightPluginDaemonTest {

  ChangeCheckerImpl.Factory changeCheckerFactory;

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();
    changeCheckerFactory = plugin.getSysInjector().getInstance(ChangeCheckerImpl.Factory.class);
  }

  @Test
  public void shouldPopulateMetaSha() throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> eventOption = changeChecker.newIndexEvent();

    assertThat(eventOption.isPresent()).isTrue();
    IndexEvent event = eventOption.get();
    assertThat(event.metaSha).isNotNull();
    assertThat(event.metaSha).isEqualTo(readMetaSha(change));
  }

  @Test
  public void shouldReturnIsUpToDateTrueWhenEventContainsCorrectMetaAndTargetSha()
      throws Exception {
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event = changeChecker.newIndexEvent();

    ReviewInput reviewInput = new ReviewInput();
    ReviewInput.CommentInput comment = createCommentInput(1, 0, 1, 1, "Test comment");
    reviewInput.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(comment));
    gApi.changes().id(change.getChangeId()).current().review(reviewInput);

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
  public void shouldReturnFalseWhenMetaShaIsNotUpToDate() throws Exception {
    String testMetaRefSha = "6212efebe6e8b9f439a8ad013243e602afab7441";
    Result change = createChange();
    ChangeChecker changeChecker = changeCheckerFactory.create(change.getChangeId());
    Optional<IndexEvent> event =
        changeChecker
            .newIndexEvent()
            .map(
                e -> {
                  e.metaSha = testMetaRefSha;
                  return e;
                });

    assertThat(changeChecker.isChangeUpToDate(event)).isFalse();
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

  private String readMetaSha(Result change) throws IOException {
    try (Repository repo = repoManager.openRepository(change.getChange().change().getProject())) {
      String refName = RefNames.changeMetaRef(change.getChange().getId());
      Ref ref = repo.exactRef(refName);
      if (ref == null) {
        return null;
      }

      return ref.getTarget().getObjectId().getName();
    }
  }

  private ReviewInput.CommentInput createCommentInput(
      int startLine, int startCharacter, int endLine, int endCharacter, String message) {
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.range = new Comment.Range();
    comment.range.startLine = startLine;
    comment.range.startCharacter = startCharacter;
    comment.range.endLine = endLine;
    comment.range.endCharacter = endCharacter;
    comment.message = message;
    comment.path = Patch.COMMIT_MSG;
    return comment;
  }
}
